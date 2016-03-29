package net.lr.maven.osgi.indexer;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.indexer.ResourceIndexer;
import org.osgi.service.indexer.impl.KnownBundleAnalyzer;
import org.osgi.service.indexer.impl.RepoIndex;

/**
 * Copy project dependencies to target dir and creates OSGi R5 index format with relative urls
 */
@Mojo(name = "index", defaultPhase = PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class IndexerMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject				project;

	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
	private RepositorySystemSession		session;

	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	private File						targetDir;

	@Parameter(property = "bnd.indexer.scopes", readonly = true, required = false)
	private List<String>				scopes;

	@Component
	private RepositorySystem			system;

	@Component
	private ProjectDependenciesResolver	resolver;

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (scopes == null || scopes.isEmpty()) {
			scopes = Arrays.asList("compile", "runtime");
		}
		try {
			DependencyResolutionRequest request = new DefaultDependencyResolutionRequest(project, session);
			request.setResolutionFilter(new ScopeFilter());
			DependencyResolutionResult result = resolver.resolve(request);
			if (result.getDependencyGraph() == null || result.getDependencyGraph().getChildren().isEmpty()) {
				return;
			}
			Map<File,ArtifactResult> dependencies = new HashMap<>();
			discoverArtifacts(dependencies, result.getDependencyGraph().getChildren(), project.getArtifact().getId());

			RepoIndex indexer = new RepoIndex();
			indexer.addAnalyzer(new KnownBundleAnalyzer(), allJarsFilter());
			File outputFile = new File(targetDir, "index.xml");
			targetDir.mkdirs();
			OutputStream output = new FileOutputStream(outputFile);
			getLog().debug("Indexing artifacts: " + dependencies.keySet());
			Map<String,String> config = new HashMap<String,String>();
			config.put(ResourceIndexer.PRETTY, "true");
			config.put(ResourceIndexer.ROOT_URL, targetDir.getAbsolutePath());
			indexer.index(dependencies.keySet(), output, config);
			File gzipOutputFile = zip(outputFile);

			attach(outputFile, "osgi-index", "xml");
			attach(gzipOutputFile, "osgi-index", "xml.gz");
		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	private Filter allJarsFilter() throws MojoExecutionException {
		Filter filter;
		try {
			filter = FrameworkUtil.createFilter("(name=*.jar)");
		} catch (InvalidSyntaxException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
		return filter;
	}

	private File zip(File outputFile) throws MojoExecutionException {
		File gzipOutputFile = new File(outputFile.getPath() + ".gz");

		try (InputStream is = new BufferedInputStream(new FileInputStream(outputFile));
				OutputStream gos = new GZIPOutputStream(new FileOutputStream(gzipOutputFile))) {
			byte[] bytes = new byte[4096];
			int read;
			while ((read = is.read(bytes)) != -1) {
				gos.write(bytes, 0, read);
			}
		} catch (IOException ioe) {
			throw new MojoExecutionException("Unable to zip index file");
		}
		return gzipOutputFile;
	}

	private void attach(File file, String type, String extension) {
		DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
		handler.setExtension(extension);
		DefaultArtifact artifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(),
				project.getVersion(), null, type, null, handler);
		artifact.setFile(file);
		project.addAttachedArtifact(artifact);
	}

	private final class ScopeFilter implements DependencyFilter {
		@Override
		public boolean accept(DependencyNode node, List<DependencyNode> parents) {
			if (node.getDependency() != null) {
				return scopes.contains(node.getDependency().getScope());
			}
			return false;
		}
	}

	private void discoverArtifacts(Map<File,ArtifactResult> files, List<DependencyNode> nodes, String parent)
			throws ArtifactResolutionException, IOException {
		for (DependencyNode node : nodes) {

			System.out.println(node.getDependency());
			if (!scopes.contains(node.getDependency().getScope())) {
				continue;
			}
			ArtifactRequest request = new ArtifactRequest(node.getArtifact(), project.getRemoteProjectRepositories(),
					parent);
			ArtifactResult resolvedArtifact = system.resolveArtifact(session, request);
			if ("jar".equals(node.getArtifact().getExtension())) {
				File artifactFile = resolvedArtifact.getArtifact().getFile();
				getLog().debug("Located file: " + artifactFile + " for artifact " + resolvedArtifact);
				File baseDir = session.getLocalRepository().getBasedir();
				String relativePath = artifactFile.getAbsolutePath().substring(baseDir.getAbsolutePath().length());
				File destFile = new File(targetDir, relativePath);
				Files.createDirectories(destFile.getParentFile().toPath());
				Files.copy(artifactFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				files.put(destFile, resolvedArtifact);
			}
			discoverArtifacts(files, node.getChildren(), node.getRequestContext());
		}
	}

}
