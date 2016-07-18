package net.lr.maven.osgi.indexer;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
import org.eclipse.aether.graph.Dependency;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Copy project dependencies to target dir and creates OSGi R5 index format with relative urls
 */
@Mojo(name = "index", defaultPhase = PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class IndexerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession session;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File targetDir;

    @Parameter(property = "bnd.indexer.scopes", readonly = true, required = false)
    private List<String> scopes;

    @Component
    private RepositorySystem system;

    @Component
    private ProjectDependenciesResolver resolver;

    public void execute() throws MojoExecutionException, MojoFailureException {
        File localRepo = session.getLocalRepository().getBasedir();
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
            for (Dependency dep : result.getDependencies()) {
                System.out.println(dep.getArtifact().getFile());
            }
            Set<File> dependencies = new HashSet<File>();
            discoverArtifacts(dependencies, result.getDependencyGraph().getChildren(),
                              project.getArtifact().getId());

            RepoIndex indexer = new RepoIndex();
            indexer.addAnalyzer(new KnownBundleAnalyzer(), allJarsFilter());
            File outputFile = new File(targetDir, "index.xml");
            OutputStream output = new FileOutputStream(outputFile);
            getLog().debug("Indexing artifacts: " + dependencies);
            Map<String, String> config = new HashMap<String, String>();
            config.put(ResourceIndexer.PRETTY, "true");
            config.put(ResourceIndexer.ROOT_URL, "/");
            indexer.index(dependencies, output, config);
            attach(outputFile, "osgi-index", "xml");
            File replacedFile = new File(targetDir, "index2.xml");
            replaceReactorBundles(outputFile, replacedFile);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void replaceReactorBundles(File indexFile, File replacedFile) throws Exception {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        builder = builderFactory.newDocumentBuilder();
        Document document = builder.parse(new FileInputStream(indexFile));
        NodeList elements = document.getElementsByTagName("attribute");
        for (int c = 0; c < elements.getLength(); c++) {
            Element element = (Element)elements.item(c);
            if (element.getTagName().equals("attribute") && element.getAttribute("name").equals("url")) {
                String url = element.getAttribute("value");
                String absolutePath = getBundleFile(new File("/", url)).getAbsolutePath();
                element.setAttribute("value", absolutePath);
                System.out.println(url + " - " + absolutePath);
            }
        }
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(indexFile);
        transformer.transform(source, result);
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

    private void discoverArtifacts(Set<File> files, List<DependencyNode> nodes, String parent)
        throws ArtifactResolutionException, IOException {
        for (DependencyNode node : nodes) {
            if (!scopes.contains(node.getDependency().getScope())) {
                continue;
            }
            ArtifactRequest request = new ArtifactRequest(node.getArtifact(),
                                                          project.getRemoteProjectRepositories(), parent);
            ArtifactResult resolvedArtifact = system.resolveArtifact(session, request);
            if ("jar".equals(node.getArtifact().getExtension())) {
                File artifactFile = resolvedArtifact.getArtifact().getFile();

                getLog().debug("Located file: " + artifactFile + " for artifact " + resolvedArtifact);
                files.add(artifactFile);
            }
            discoverArtifacts(files, node.getChildren(), node.getRequestContext());
        }
    }

    private File getBundleFile(File artifactFile) {
        File parent = artifactFile.getParentFile();
        return artifactFile.getAbsolutePath().contains("/target") ? new File(parent, "classes") //
            : artifactFile;
    }

}
