package utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import model.Command;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;

public class Editor
{

    private final Properties prop;
    private final String editor;

    public Editor(String workspace, String editor) throws IOException, FileNotFoundException, ConfigurationException
    {
	String config = workspace + "/config.properties";
	createConfigIfNotExists(config);

	prop = new Properties();
	prop.load(config);

	this.editor = editor;
    }

    private void createConfigIfNotExists(String path) throws IOException
    {
	File config = new File(path);
	if (!config.exists())
	{
	    if (!config.createNewFile())
	    {
		throw new IOException("Ooops! Global config.properties could not be created. \n"
			+ config.getAbsolutePath());
	    }
	}
    }

    public void run() throws Exception
    {
	if (StringUtils.isBlank(editor))
	{
	    printDefaultEditor();
	}
	else
	{
	    setDefaultEditor(editor);
	}
    }

    private void printDefaultEditor()
    {
	String oldEditor = prop.getProperty("EDITOR");
	if (!StringUtils.isBlank(oldEditor))
	{
	    System.out.println("[i] Default editor in global config: " + oldEditor);
	}
	else
	{
	    System.out.println("[i] There is no defined default editor in global config.");
	}
    }

    private void setDefaultEditor(String newEditor) throws Exception
    {
	String oldEditor = prop.getProperty("EDITOR");
	if (!StringUtils.isBlank(oldEditor))
	{
	    System.out.println("[i] There is already defined default editor in global config: " + oldEditor);
	    if (doesUserConfirm())
	    {
		setNewEditor(newEditor);
	    }
	    else
	    {
		System.out.println("[✓] Ok, default editor is not modified.");
	    }
	}
	else
	{
	    setNewEditor(newEditor);
	}
    }

    private boolean doesUserConfirm()
    {
	Scanner scanner = new Scanner(System.in);
	System.out.print("[?] Are you sure to set default editor? (y/n) ");
	String answer = scanner.next();
	return !StringUtils.isBlank(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y');
    }

    private void setNewEditor(String newEditor) throws Exception
    {
	String editorFilePath = getEditorFilePath(newEditor.trim());
	if (isEditorAlive(editorFilePath))
	{
	    prop.setProperty("EDITOR", new File(editorFilePath).getName());
	    System.out.println("[✓] Default editor is set as: " + prop.getProperty("EDITOR"));
	}
    }

    private String getEditorFilePath(String editor) throws Exception
    {
	Command cmd = new Command(new String[]
	{
	    "which", editor
	});
	if (cmd.execute() && !StringUtils.isBlank(cmd.toString()))
	{
	    return cmd.toString();
	}
	else
	{
	    throw new Exception("Ooops! Could not find editor " + editor + " in global path! \n"
		    + "Failed: " + cmd.getCommandString());
	}
    }

    private boolean isEditorAlive(String editor) throws IOException
    {
	Process process = Runtime.getRuntime().exec(editor);
	if (process.isAlive())
	{
	    process.destroy();
	    return true;
	}
	else
	{
	    throw new IOException("Ooops! Editor could not be triggered. Please check whether command is executable. \n"
		    + editor);
	}
    }
}
