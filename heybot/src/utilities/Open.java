package utilities;

import model.Command;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class Open
{

    private String operationFilePath;
    private String editorFilePath;

    public Open(String workspace, String operation) throws Exception {
        setOperationFilePath(workspace + "/" + operation);
        setEditorFilePath(workspace);
    }

    private void setEditorFilePath(String workspace) throws Exception {
        String config = workspace + "/config.properties";
        if (!doesConfigExist(config)) {
            System.out.println("[e] Default editor not set yet! Please, use -e,--editor option to set your favorite editor.");
            throw new Exception("");
        }

        Properties prop = new Properties();
        prop.load(config);

        String editor = prop.getProperty("EDITOR");
        if (StringUtils.isBlank(editor)) {
            System.out.println("[e] Default editor not set yet! Please, use -e,--editor option to set your favorite editor.");
            throw new Exception("");
        }
        editor = editor.trim();

        if (!isAlive(getEditorWithoutAnyOption(editor))) {
            System.out.println("[e] Default editor not working! Please, use -e,--editor option to fix your favorite editor.");
            throw new Exception("");
        }

        editorFilePath = editor;
    }

    private boolean doesConfigExist(String path) {
        File config = new File(path);
        return config.exists();
    }

    private String getEditorWithoutAnyOption(String editor) {
        int i = editor.indexOf(" --");
        if (i > 0) {
            return editor.substring(0, i);
        }
        return editor;
    }

    private boolean isAlive(String editor) {
        try {
            Process process = Runtime.getRuntime().exec(editor);
            if (process.isAlive()) {
                process.destroy();
                return true;
            }
        } catch (IOException e) {
            System.out.println("[e] " + e.getMessage());
        }

        return false;
    }

    private void setOperationFilePath(String operationFilePath) throws Exception {
        File operation = new File(operationFilePath);
        if (!operation.exists()) {
            throw new Exception("Ooops! Could not find " + operation.getName() + " in workspace! \n"
                + "Check operation file exists in path: " + operation.getAbsolutePath());
        }
        this.operationFilePath = operationFilePath;
    }

    public void run() throws IOException {
        System.out.println("[*] Opening operation file with editor...");
        System.out.println("[i] OpFile: " + operationFilePath);
        System.out.println("[i] Editor: " + editorFilePath);
        String cmd = editorFilePath + " " + operationFilePath + " " + "</dev/tty" + " " + ">/dev/tty";
        Command shC = new Command(new String[]{"sh", "-c", cmd});
        System.out.println("[*] " + shC.getCommandStringWithNoEscapeWhitespace()
            .replace(cmd, "\"" + cmd + "\""));
        String md5BeforeExec = "";
        try (InputStream is = Files.newInputStream(Paths.get(operationFilePath))) {
            md5BeforeExec = DigestUtils.md5Hex(is);
        }
        if (shC.execute()) {
            if (!StringUtils.isBlank(shC.toString())) {
                System.out.println(shC.toString());
            }
            try (InputStream is = Files.newInputStream(Paths.get(operationFilePath))) {
                System.out.print("[✓] File Status: ");
                if (md5BeforeExec.length() > 0) {
                    if (DigestUtils.md5Hex(is).equalsIgnoreCase(md5BeforeExec)) {
                        System.out.println("No change.");
                    } else {
                        System.out.println("Modified.");
                    }
                } else {
                    System.out.println("Unknown.");
                }
            }
        }
    }

}
