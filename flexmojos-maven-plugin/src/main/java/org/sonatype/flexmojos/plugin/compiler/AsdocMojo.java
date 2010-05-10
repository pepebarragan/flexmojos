/**
 * Flexmojos is a set of maven goals to allow maven users to compile, optimize and test Flex SWF, Flex SWC, Air SWF and Air SWC.
 * Copyright (C) 2008-2012  Marvin Froeder <marvin@flexmojos.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.sonatype.flexmojos.plugin.compiler;

import static ch.lambdaj.Lambda.filter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.sonatype.flexmojos.matcher.artifact.ArtifactMatcher.type;
import static org.sonatype.flexmojos.plugin.common.FlexExtension.SWC;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.model.PatternSet;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.UnArchiver;
import org.sonatype.flexmojos.compatibilitykit.FlexCompatibility;
import org.sonatype.flexmojos.compiler.IASDocConfiguration;
import org.sonatype.flexmojos.compiler.IPackagesConfiguration;
import org.sonatype.flexmojos.compiler.IRuntimeSharedLibraryPath;
import org.sonatype.flexmojos.compiler.command.Result;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenRuntimeException;
import org.sonatype.flexmojos.plugin.utilities.MavenUtils;
import org.sonatype.flexmojos.util.OSUtils;
import org.sonatype.flexmojos.util.PathUtil;

/**
 * <p>
 * Goal which generates documentation from the ActionScript sources.
 * </p>
 * 
 * @author Marvin Herman Froeder (velo.br@gmail.com)
 * @since 4.0
 * @goal asdoc
 * @requiresDependencyResolution compile
 * @phase process-sources
 * @configurator flexmojos
 */
public class AsdocMojo
    extends AbstractMavenFlexCompilerConfiguration<IASDocConfiguration, AsdocMojo>
    implements IASDocConfiguration, IPackagesConfiguration, Mojo
{

    /**
     * If true, will treat multi-modules projects as only one project otherwise will generate Asdoc per project
     * 
     * @parameter default-value="false" expression="${flex.asdoc.aggregate}"
     */
    protected boolean aggregate;

    /**
     * Specifies whether to include date with footer
     * <p>
     * Equivalent to -date-in-footer
     * </p>
     * 
     * @parameter expression="${flex.dateInFooter}"
     */
    private Boolean dateInFooter;

    /**
     * Automatically document all declared namespaces
     * 
     * @parameter default-value="false" expression="${flex.docAllNamespaces}"
     */
    private boolean docAllNamespaces;

    /**
     * List of classes to include in the documentation
     * <p>
     * Equivalent to -doc-classes
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;docClasses&gt;
     *   &lt;docClass&gt;
     *     &lt;includes&gt;
     *       &lt;include&gt;com/mycompany/*&lt;/include&gt;
     *     &lt;/includes&gt;
     *     &lt;excludes&gt;
     *       &lt;exclude&gt;com/mycompany/ui/*&lt;/exclude&gt;
     *     &lt;/excludes&gt;
     *   &lt;/docClass&gt;
     * &lt;/docClasses&gt;
     * </pre>
     * 
     * @parameter
     */
    private PatternSet[] docClasses;

    /**
     * List of namespaces to include in the documentation
     * <p>
     * Equivalent to -doc-namespaces
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;docNamespaces&gt;
     *   &lt;namespace&gt;http://mynamespace.com&lt;/namespace&gt;
     * &lt;/docNamespaces&gt;
     * </pre>
     * 
     * @parameter
     */
    private List<String> docNamespaces;

    /**
     * List of source file to include in the documentation
     * <p>
     * Equivalent to -doc-sources
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;docSources&gt;
     *   &lt;docSource&gt;${project.build.sourceDirectory}&lt;/docSource&gt;
     * &lt;/docSources&gt;
     * </pre>
     * 
     * @parameter
     */
    private File[] docSources;

    /**
     * Path to look for the example files
     * <p>
     * Equivalent to -examples-path
     * </p>
     * 
     * @parameter expression="${flex.examplesPath}"
     */
    private File examplesPath;

    /**
     * Boolean specifying whether to exclude dependencies
     * <p>
     * Equivalent to -exclude-dependencies
     * </p>
     * 
     * @parameter expression="${flex.excludeDependencies}"
     */
    private Boolean excludeDependencies;

    /**
     * Footer string to be displayed in the documentation
     * <p>
     * Equivalent to -footer
     * </p>
     * 
     * @parameter default-value="Generated by Flexmojos" expression="${flex.footer}"
     */
    private String footer;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -include-all-for-asdoc
     * </p>
     * include-all-only is only for internal use
     * 
     * @parameter expression="${flex.includeAllForAsdoc}"
     */
    private Boolean includeAllForAsdoc;

    /**
     * If true, manifest entries with lookupOnly=true are included in SWC catalog
     * <p>
     * Equivalent to -include-lookup-only
     * </p>
     * 
     * @parameter expression="${flex.includeLookupOnly}"
     */
    private Boolean includeLookupOnly;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -keep-xml
     * </p>
     * 
     * @parameter expression="${flex.keepXml}"
     */
    private Boolean keepXml;

    /**
     * Width of the left frame
     * <p>
     * Equivalent to -left-frameset-width
     * </p>
     * 
     * @parameter expression="${flex.leftFramesetWidth}"
     */
    private Integer leftFramesetWidth;

    /**
     * Report well-formed HTML errors as warnings
     * <p>
     * Equivalent to -lenient
     * </p>
     * 
     * @parameter expression="${flex.lenient}"
     */
    private Boolean lenient;

    /**
     * Title to be displayed in the title bar
     * <p>
     * Equivalent to -main-title
     * </p>
     * 
     * @parameter default-value="${project.name} Documentation" expression="${flex.mainTitle}"
     */
    private String mainTitle;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -restore-builtin-classes
     * </p>
     * Restore-builtin-classes is only for internal use
     * 
     * @parameter expression="${flex.restoreBuiltinClasses}"
     */
    private Boolean restoreBuiltinClasses;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -skip-xsl
     * </p>
     * 
     * @parameter expression="${flex.skipXsl}"
     */
    private Boolean skipXsl;

    private File templatePath;

    /**
     * Title to be displayed in the browser window
     * <p>
     * Equivalent to -window-title
     * </p>
     * 
     * @parameter default-value="${project.name} Documentation" expression="${flex.windowTitle}"
     */
    private String windowTitle;

    /**
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    protected List<MavenProject> reactorProjects;

    @Override
    public File[] getSourcePath()
    {
        if ( aggregate )
        {
            List<File> files = new ArrayList<File>();

            for ( MavenProject p : reactorProjects )
            {
                files.addAll( PathUtil.getExistingFilesList( p.getCompileSourceRoots() ) );
            }

            return files.toArray( new File[0] );
        }
        else
        {
            return super.getSourcePath();
        }
    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( aggregate && !project.isExecutionRoot() )
        {
            getLog().info( "Skipping asdoc execution, aggregate mode active." );
            return;
        }

        if ( !PathUtil.exist( getSourcePath() ) )
        {
            getLog().warn( "Skipping asdoc, source path doesn't exist." );
            return;
        }

        executeCompiler( this, true );
    }

    @Override
    public Result doCompile( IASDocConfiguration cfg, boolean synchronize )
        throws Exception
    {
        return compiler.asdoc( cfg, synchronize );
    }

    public Boolean getDateInFooter()
    {
        return dateInFooter;
    }

    public List<String> getDocClasses()
    {
        if ( docClasses == null )
        {
            return null;
        }

        return filterClasses( docClasses, getSourcePath() );
    }

    public List<String> getDocNamespaces()
    {
        if ( docNamespaces != null )
        {
            return docNamespaces;
        }

        if ( docAllNamespaces )
        {
            return getNamespacesUri();
        }

        return null;
    }

    public File[] getDocSources()
    {
        if ( docSources == null && getDocNamespaces() == null && docClasses == null )
        {
            return getSourcePath();
        }

        return docSources;
    }

    public String getExamplesPath()
    {
        return PathUtil.getCanonicalPath( examplesPath );
    }

    public List<String> getExcludeClasses()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public Boolean getExcludeDependencies()
    {
        return excludeDependencies;
    }

    public File[] getExcludeSources()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public String getFooter()
    {
        return footer;
    }

    public Boolean getIncludeAllForAsdoc()
    {
        return includeAllForAsdoc;
    }

    public Boolean getIncludeLookupOnly()
    {
        return includeLookupOnly;
    }

    public Boolean getKeepXml()
    {
        return keepXml;
    }

    public Integer getLeftFramesetWidth()
    {
        return leftFramesetWidth;
    }

    public Boolean getLenient()
    {
        return lenient;
    }

    public String getMainTitle()
    {
        return mainTitle;
    }

    public String[] getPackage()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public String getPackageDescriptionFile()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public IPackagesConfiguration getPackagesConfiguration()
    {
        return this;
    }

    public Boolean getRestoreBuiltinClasses()
    {
        return restoreBuiltinClasses;
    }

    public Boolean getSkipXsl()
    {
        return skipXsl;
    }

    public String getTemplatesPath()
    {
        if ( templatePath != null )
        {
            return PathUtil.getCanonicalPath( templatePath );
        }

        File templateOutput = new File( project.getBuild().getDirectory(), "templates" );
        templateOutput.mkdirs();

        Artifact template = resolve( "com.adobe.flex.compiler", "asdoc", getCompilerVersion(), "template", "zip" );
        try
        {
            UnArchiver unarchiver = archiverManager.getUnArchiver( "zip" );
            unarchiver.setDestDirectory( templateOutput );
            unarchiver.setSourceFile( template.getFile() );
            unarchiver.extract();
        }
        catch ( Exception e )
        {
            throw new MavenRuntimeException( "Unable to unpack asdoc template", e );
        }

        makeAsdocExecutable( templateOutput );

        return PathUtil.getCanonicalPath( templateOutput );
    }

    @FlexCompatibility( maxVersion = "4.0.0.3127" )
    private void makeAsdocExecutable( File templateOutput )
    {
        // must use chmod to make asdoc executable
        if ( !OSUtils.isWindows() )
        {
            Runtime runtime = Runtime.getRuntime();
            String pathname =
                String.format( "%s/%s", templateOutput.getAbsolutePath(), "asDocHelper"
                    + ( MavenUtils.isLinux() ? ".linux" : "" ) );
            String[] statements = new String[] { "chmod", "u+x", pathname };
            try
            {
                Process p = runtime.exec( statements );
                int result = p.waitFor();
                if ( 0 != result )
                {
                    throw new MavenRuntimeException( String.format( "Unable to execute %s. Return value = %d",
                                                                    Arrays.asList( statements ), result ) );
                }
            }
            catch ( Exception e )
            {
                throw new MavenRuntimeException( String.format( "Unable to execute %s", Arrays.asList( statements ) ) );
            }
        }
    }

    public String getWindowTitle()
    {
        return windowTitle;
    }

    public String getOutput()
    {
        File output = new File( getTargetDirectory(), "asdoc" );
        output.mkdirs();
        return PathUtil.getCanonicalPath( output );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public File[] getExternalLibraryPath()
    {
        return MavenUtils.getFiles( getGlobalArtifact() );
    }

    @Override
    public IRuntimeSharedLibraryPath[] getRuntimeSharedLibraryPath()
    {
        return null;
    }

    @Override
    public File[] getIncludeLibraries()
    {
        return null;
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public File[] getLibraryPath()
    {
        if ( aggregate )
        {
            Set<File> deps = new LinkedHashSet<File>();

            for ( MavenProject p : reactorProjects )
            {

                ArtifactResolutionRequest req = new ArtifactResolutionRequest();
                req.setArtifact( p.getArtifact() );
                req.setResolveTransitively( true );
                req.setLocalRepository( localRepository );
                req.setRemoteRepositories( remoteRepositories );
                req.setManagedVersionMap( p.getManagedVersionMap() );

                ArtifactResolutionResult res = repositorySystem.resolve( req );

                deps.addAll( MavenUtils.getFilesSet( filter( allOf( type( SWC ), not( GLOBAL_MATCHER ) ),
                                                             res.getArtifacts() ) ) );
            }

            return deps.toArray( new File[0] );
        }
        else
        {
            return MavenUtils.getFiles( getDependencies( type( SWC ) ) );
        }
    }
}
