package ca.kognitivit.ant;

import java.io.File;

public class RelativePathMapping
{
	private String _pattern;
	private File _path;

	public File getPath()
	{
		return _path;
	}

	public String getPattern()
	{
		return _pattern;
	}

	public void setPattern(String value)
	{
		_pattern = value;
	}

	public void setPath(File value)
	{
		_path = value;
	}
}
