package utilities;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import model.Command;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;

public class Open
{

    private String operationFilePath;
    private String editorFilePath;

    public Open(String workspace, String operation) throws Exception
    {
	setOperationFilePath(workspace + "/" + operation);
	setEditorFilePath(workspace);
    }

    private void setEditorFilePath(String workspace) throws IOException, ConfigurationException, Exception
    {
	String config = workspace + "/config.properties";
	createConfigIfNotExists(config);

	Properties prop = new Properties();
	prop.load(config);

	String editor = prop.getProperty("EDITOR");
	if (StringUtils.isBlank(editor))
	{
	    editor = getEditorFilePath(getEditorInteractive());
	}
	else
	{
	    editor = getEditorFilePath(editor.trim());
	}

	editorFilePath = isEditorAlive(editor) ? editor : "";
	prop.setProperty("EDITOR", new File(editorFilePath).getName());
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

    private String getEditorInteractive() throws Exception
    {
	Scanner scanner = new Scanner(System.in);
	System.out.println("[w] Heybot default editor is not configured yet.");
	System.out.print("[?] What is your favorite editor to use with heybot? ");
	String answer = scanner.next();
	if (StringUtils.isBlank(answer))
	{
	    throw new Exception("Ooops! Heybot default editor is not configured.");
	}

	return answer;
    }

    private void setOperationFilePath(String operationFilePath) throws Exception
    {
	File operation = new File(operationFilePath);
	if (!operation.exists())
	{
	    throw new Exception("Ooops! Could not find " + operation.getName() + " in workspace! \n"
		    + "Check operation file exists in path: " + operation.getAbsolutePath());
	}
	this.operationFilePath = operationFilePath;
    }

    public void run()
    {
	System.out.println("[*] Opening operation file with editor... \n"
		+ "\n Operation: " + operationFilePath
		+ "\n Editor: " + editorFilePath + "\n");

	Command cmd = new Command(new String[]
	{
	    editorFilePath, operationFilePath
	});
	System.out.println(cmd.getCommandString());
	System.out.println("[*] ...");
	cmd.executeNoWait();
    }

}
