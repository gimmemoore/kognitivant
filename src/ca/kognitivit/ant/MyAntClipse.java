package ca.kognitivit.ant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 * Ant task used to process an Eclipse classpath file and inject information from it into the ant classpath
 * @author Kevin Moore
 */
public class MyAntClipse extends Task
{
	private List<FileSet> _additionalFiles = new ArrayList<FileSet>();
	private List<ContainerEntry> _containers = new ArrayList<ContainerEntry>();
	private String _filename = ".classpath";
	private List<RelativePathMapping> _libraryPathMapping = new ArrayList<RelativePathMapping>();
	private String _pathId;
	private boolean _verbose = false;
	private File _workingDirectory;

	/**
	 * This method is the main entry point into the ant task
	 */
	public void execute() throws BuildException
	{
		if (_verbose)
			log("starting execution");

		Document document = getXmlDocument();
		Path classPath = getClasspath();
		List<?> children = getEclipseClasspathEntry(document);

		if (children == null) {
			return;
		}

		// iterate over each classpath entry found in the eclipse classpath file
		for (Iterator<?> i = children.iterator(); i.hasNext();) {
			Element element = (Element) i.next();
			Attribute kind = element.getAttribute("kind");
			Attribute path = element.getAttribute("path");

			if (_verbose)
				log("found " + kind.getValue() + " with " + path.getValue());

			// Extract the jar files that are present at the provided location
			List<String> location = extractFilesFromClasspath(kind, path);
			if (location == null) {
				continue;
			}

			// Add jar files found from the eclipse classpath into the ant classpath
			addEclipseClasspathToClasspath(classPath, kind, location);
		}

		// Add jar from the additional fileset into the ant classpath
		addFilesetsToClasspath(classPath);

		// Set the new path into the project
		getProject().addReference(_pathId, classPath);

		if (_verbose)
			log("\n" + _pathId + " : " + classPath.toString());
	}

	/**
	 * Get eclipse classpath entries from the .classpath XML file
	 * @param document the XML document from which to extract the classpathentry
	 * @return The classpathentry nodes
	 */
	private List<?> getEclipseClasspathEntry(Document document)
	{
		Element root = document.getRootElement();

		// Get all the elements that relate to the classpath and walk them
		List<?> children = root.getChildren("classpathentry");

		if (_verbose)
			log("found " + children.size() + " entries in classpath file");

		return children;
	}

	/**
	 * Add eclipse classpath files to the ant classpath
	 * @param classPath the ant classpath to which the files are to be added
	 * @param kind the kind of element
	 * @param location the location of the files to be added
	 */
	private void addEclipseClasspathToClasspath(Path classPath, Attribute kind, List<String> location)
	{
		for (String item : location) {
			try {
				File file = new File(item);

				if (!file.isAbsolute()) {
					if (kind.getValue().equals("lib") || !file.exists()) {
						for (RelativePathMapping mapping : _libraryPathMapping) {
							item = item.replaceFirst(mapping.getPattern(), Matcher.quoteReplacement(mapping.getPath().getPath()));
						}

						file = new File(item);
					}
				}

				if (_verbose)
					log("adding " + file.getPath());

				Path.PathElement e = classPath.createPathElement();
				e.setLocation(file);
			}
			catch (Exception e) {
				log("Error processing [" + location + "]: " + e.getMessage());
			}
		}
	}

	/**
	 * Extract the jar files from the specified path, based on the kind of entry (src, lib, con)
	 * @param kind source, library or containter (src, lib, con)
	 * @param path the path from which files will be extracted
	 * @return The list of filenames to be added to the classpath
	 */
	private List<String> extractFilesFromClasspath(Attribute kind, Attribute path)
	{
		List<String> location = new ArrayList<String>();

		if (kind.getValue().equals("lib")) {
			// In case of libraries, add the path itself
			location.add(path.getValue());
		} else if (kind.getValue().equals("con")) {
			// When encoutering a container replace it with the user container mapping from the ant file
			location = replaceContainerPaths(path.getValue());
		} else {
			location = null;
			if (!kind.getValue().equals("src") && !kind.getValue().equals("output")) {
				log("Unsupported kind of classpath entry " + kind.getValue());
			}
		}

		return location;
	}

	/**
	 * Get the classpath associated with the provided classpath id
	 * @return a new classpath if the path isn't found, otherwise returns the existing classpath entry
	 */
	private Path getClasspath()
	{
		Path classPath = null;

		if (!getProject().getReferences().containsKey(_pathId))
			classPath = new Path(getProject());
		else
			classPath = (Path) getProject().getReference(_pathId);

		return classPath;
	}

	/**
	 * Add additional files from the user's filesets into the ant classpath
	 * @param classPath the classpath into which files will be added
	 */
	private void addFilesetsToClasspath(Path classPath)
	{
		for (FileSet item : _additionalFiles) {
			for (String file : item.getDirectoryScanner().getIncludedFiles()) {
				if (_verbose)
					log("Adding file " + item.getDir().getPath() + "\\" + file);

				Path.PathElement e = classPath.createPathElement();
				e.setLocation(new File(item.getDir().getPath(), file));
			}

			for (String file : item.getDirectoryScanner().getIncludedDirectories()) {
				if (_verbose)
					log("Adding folder " + item.getDir().getPath() + "\\" + file);

				Path.PathElement e = classPath.createPathElement();
				e.setLocation(new File(item.getDir().getPath(), file));
			}
		}
	}

	/**
	 * Extract jar files recursively or not from the specified path
	 * @param path the path from which files are to be extracted
	 * @param recursive true to recurse over children, false otherwise
	 * @return the list of filenames extracted
	 */
	private List<String> extractJarFiles(File path, boolean recursive)
	{
		List<String> result = new ArrayList<String>();

		if (_verbose)
			log("extracting jar files from folder " + path);

		String[] children = path.list();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				// Get filename of file or directory
				File file = new File(children[i]);

				if (recursive && file.isDirectory() && !file.getName().equals(".svn")) {
					result.addAll(extractJarFiles(file, true));
				} else if (children[i].endsWith(".jar")) {
					result.add(path + "\\" + file.getPath());
				}
			}
		}

		return result;
	}

	/**
	 * Replaces the containers path with the user's specified ones
	 * @param containerName the name of the container defined in the eclipse classpath
	 * @return the list of jar files found in the containers
	 */
	private List<String> replaceContainerPaths(String containerName)
	{
		if (_verbose)
			log("replacing my eclipse path in : " + containerName);

		for (ContainerEntry entry : _containers) {
			if (_verbose)
				log("checking : " + containerName + " with " + entry.getName());

			if (containerName.equals(entry.getName())) {
				return extractJarFiles(entry.getPath(), true);
			}
		}

		return null;
	}

	/**
	 * Get the classpath document in a XML format
	 * @return A Document to be navigated
	 */
	private Document getXmlDocument()
	{
		if (_verbose)
			log("fetching classpath file into XML document");

		Document document = null;
		File classpathFile = null;

		try {
			// if the user skipped the directory, use the current directory
			if (_workingDirectory == null) {
				_workingDirectory = getProject().getBaseDir();
			}

			log("using working directory : " + _workingDirectory + " and file : " + _filename);

			classpathFile = new File(_workingDirectory, _filename);

			SAXBuilder sax = new SAXBuilder();
			document = sax.build(classpathFile);

			if (_verbose)
				log("classpath successfuly loaded into XML document");
		}
		catch (FileNotFoundException e) {
			log("Unable to open Eclipse classpath file [" + classpathFile.getAbsolutePath() + "]");
		}
		catch (JDOMException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		return document;
	}

	/**
	 * Method required to support filesets as a nested tag of this task
	 * @param fileset the fileset that will be added
	 */
	public void addFileset(FileSet fileset)
	{
		log("Adding fileset");

		_additionalFiles.add(fileset);
	}

	/**
	 * Required method to support ContainerEntry as a nested tag
	 * @param value The container entry to add, which consists in the mapping of a container name and location
	 */
	public void addConfiguredContainerEntry(ContainerEntry value)
	{
		if (_verbose)
			log("Adding container mapping (" + value.getName() + ", " + value.getPath() + ")");

		_containers.add(value);
	}

	/**
	 * Required method to support RelativePathMapping as a nested tag
	 * @param value a relative path mapping for the library type. This will replace the pattern with the specified path
	 */
	public void addConfiguredRelativePathMapping(RelativePathMapping value)
	{
		if (_verbose)
			log("Adding relative path mapping (pattern " + value.getPattern() + ", " + value.getPath() + ")");

		_libraryPathMapping.add(value);
	}

	/**
	 * The task working directory
	 * @param directory the path from which the task will load the .classpath file, if null the task uses the value of the dir attribute in the ant script
	 */
	public void setDir(File directory)
	{
		this._workingDirectory = directory;
	}

	/**
	 * set the path id into which the jar files will be added
	 * @param pathName the pathname (reference) into which files will be added
	 */
	public void setPathId(String pathName)
	{
		this._pathId = pathName;
	}

	/**
	 * When set to true, output detailed messages about the inner processing of the task
	 * @param verbose true to display detailed log, false otherwise
	 */
	public void setVerbose(boolean verbose)
	{
		this._verbose = verbose;
	}
}
