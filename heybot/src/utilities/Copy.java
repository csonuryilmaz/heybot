package utilities;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

public class Copy
{

    private final File source, target;

    public Copy(String workspace, CommandLine line) throws Exception
    {
	if (line.getArgs().length == 0)
	{
	    throw new Exception("Ooops! Target argument is empty.");
	}

	source = new File(workspace + "/" + getFileName(line.getOptionValue("copy").trim()));
	target = new File(workspace + "/" + getFileName(line.getArgs()[0].trim()));

	if (!source.exists())
	{
	    throw new Exception("Ooops! Source operation doesn't exist in workspace. \n"
		    + source.getAbsolutePath());
	}
    }

    private String getFileName(String arg)
    {
	return arg.endsWith(".hb") ? arg : arg + ".hb";
    }

    public void run() throws Exception
    {
	if (target.exists())
	{
	    if (doesUserConfirm())
	    {
		copy();
	    }
	}
	else
	{
	    copy();
	}    
    }

    private boolean doesUserConfirm()
    {
	Scanner scanner = new Scanner(System.in);
	System.out.println("[w] Target operation exists in workspace.");
	System.out.print("[?] Are you sure to override target operation? (y/n) ");
	String answer = scanner.next();
	return !StringUtils.isBlank(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y');
    }

    private void copy() throws IOException
    {
	FileUtils.copyFile(source, target);
	System.out.println("[✓] Copied.");
    }
}
