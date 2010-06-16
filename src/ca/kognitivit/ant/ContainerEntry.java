package ca.kognitivit.ant;

import java.io.File;

public class ContainerEntry
{
	private String _name;
	private File _path;

	public String getName(){
		return _name;
	}

	public File getPath(){
		return _path;
	}

	public void setName(String value)
	{
		_name = value;
	}

	public void setPath(File value)
	{
		_path = value;
	}
}
