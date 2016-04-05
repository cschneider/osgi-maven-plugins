package net.lr.maven.bndrun;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;

import java.io.File;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import aQute.bnd.build.Container;
import aQute.bnd.build.StandaloneRun;
import aQute.bnd.osgi.Jar;
import biz.aQute.resolve.ProjectResolver;

@Mojo(name = "resolve", defaultPhase = PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ResolverMojo extends AbstractMojo {

	@Parameter(readonly = true, required = true)
	private File	bndrun;

	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	private File	targetDir;

	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			if (!bndrun.exists()) {
				throw new MojoExecutionException("Could not find bnd run file " + bndrun);
			}
			StandaloneRun run = new StandaloneRun(bndrun);
			ProjectResolver projectResolver = new ProjectResolver(run);
			List<Container> runBundles = projectResolver.getRunBundles();
			run.getRunbundles();
			run.setRunBundles(runBundles);
			Jar jar = run.getProjectLauncher().executable();
			targetDir.mkdirs();
			File jarFile = getExportJarFile();
			jar.write(jarFile);
			projectResolver.close();
		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	private File getExportJarFile() {
		String nameExt = bndrun.getName();
		int pos = nameExt.lastIndexOf(".");
		String name = nameExt.substring(0, pos);
		File jarFile = new File(targetDir, name + ".jar");
		return jarFile;
	}

}
