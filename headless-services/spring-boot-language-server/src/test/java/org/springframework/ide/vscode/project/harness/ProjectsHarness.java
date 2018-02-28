/*******************************************************************************
 * Copyright (c) 2016, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.project.harness;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.maven.MavenBuilder;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.maven.java.classpathfile.JavaProjectWithClasspathFile;
import org.springframework.ide.vscode.commons.util.IOUtil;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.Files;

import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

/**
 * Test projects harness
 *
 * @author Alex Boyko
 * @author Kris De Volder
 */
public class ProjectsHarness {

	public static final ProjectsHarness INSTANCE = new ProjectsHarness();;

	public Cache<Object, IJavaProject> cache = CacheBuilder.newBuilder().concurrencyLevel(1).build();

	/**
	 * A callback that is given a chance to make changes to test project contents before the test project
	 * is created from it.
	 */
	public interface ProjectCustomizer {
		void customize(CustomizableProjectContent projectContent) throws Exception;
	}

	/**
	 * Provides convenience apis to make changes to project content from a {@link ProjectCustomizer}
	 */
	public static class CustomizableProjectContent {
		private final File projectRoot;

		public CustomizableProjectContent(File projectRoot) {
			this.projectRoot = projectRoot;
		}

		public void createFile(String path, String content) throws Exception {
			File target = new File(projectRoot, path);
			IOUtil.pipe(new ByteArrayInputStream(content.getBytes("UTF8")), target);
		}

		public void createType(String fqName, String sourceCode) throws Exception {
			String sourceFile = sourceFolder()+"/"+fqName.replace('.', '/')+".java";
			createFile(sourceFile, sourceCode);
		}

		private String sourceFolder() {
			return "src/main/java";
		}
	}

	private enum ProjectType {
		MAVEN,
		CLASSPATH_TXT
	}

	private ProjectsHarness() {
	}

	public IJavaProject project(ProjectType type, String name, ProjectCustomizer customizer) throws Exception {
		Tuple3<ProjectType, String, ProjectCustomizer> key = Tuples.of(type, name, customizer);
		return cache.get(key, () -> {
			Path baseProjectPath = getProjectPath(name);
			File testProjectRoot = Files.createTempDir();
			FileUtils.copyDirectory(baseProjectPath.toFile(), testProjectRoot);
			customizer.customize(new CustomizableProjectContent(testProjectRoot));
			return createProject(type, testProjectRoot.toPath());
		});
	}

	private IJavaProject createProject(ProjectType type, Path testProjectPath) throws Exception {
		switch (type) {
		case MAVEN:
			MavenBuilder.newBuilder(testProjectPath).clean().pack().javadoc().skipTests().execute();
			return new MavenJavaProject(MavenCore.getDefault(), testProjectPath.resolve(MavenCore.POM_XML).toFile());
		case CLASSPATH_TXT:
			MavenBuilder.newBuilder(testProjectPath).clean().pack().skipTests().execute();
			return new JavaProjectWithClasspathFile(testProjectPath.resolve(MavenCore.CLASSPATH_TXT).toFile());
		default:
			throw new IllegalStateException("Bug!!! Missing case");
		}
	}

	public IJavaProject project(ProjectType type, String name) throws Exception {
		return cache.get(type + "/" + name, () -> {
			Path testProjectPath = getProjectPath(name);
			return createProject(type, testProjectPath);
		});
	}

	protected Path getProjectPath(String name) throws URISyntaxException, IOException {
		return getProjectPathFromClasspath(name);
	}

	private Path getProjectPathFromClasspath(String name) throws URISyntaxException, IOException {
		URI resource = ProjectsHarness.class.getResource("/test-projects/" + name).toURI();
		return Paths.get(resource);
	}

	public MavenJavaProject mavenProject(String name, ProjectCustomizer customizer) throws Exception {
		return (MavenJavaProject) project(ProjectType.MAVEN, name, customizer);
	}

	public MavenJavaProject mavenProject(String name) throws Exception {
		return (MavenJavaProject) project(ProjectType.MAVEN, name);
	}

	public IJavaProject javaProjectWithClasspathFile(String name) throws Exception {
		return project(ProjectType.CLASSPATH_TXT, name);
	}

}