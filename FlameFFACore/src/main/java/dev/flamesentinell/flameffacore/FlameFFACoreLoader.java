package dev.flamesentinell.flameffacore;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository.Builder;

public class FlameFFACoreLoader implements PluginLoader {
   public void classloader(PluginClasspathBuilder builder) {
      MavenLibraryResolver resolver = new MavenLibraryResolver();
      resolver.addRepository(new Builder("central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
      resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:6.2.1"), null));
      resolver.addDependency(new Dependency(new DefaultArtifact("org.mariadb.jdbc:mariadb-java-client:3.5.3"), null));
      resolver.addDependency(new Dependency(new DefaultArtifact("com.h2database:h2:2.3.232"), null));
      builder.addLibrary(resolver);
   }
}
