package testsmell;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import testsmell.ResultsWriter;
import testsmell.TestFile;
import testsmell.TestSmellDetector;
import thresholds.DefaultThresholds;
import thresholds.Thresholds;


@Mojo(name = "test-smelllist", defaultPhase = LifecyclePhase.TEST)
public class TestSmellDetectorPlugin extends AbstractMojo {
	/**
     * Location of the test source directory.
     */
    @Parameter(defaultValue = "${project.basedir}/src/test/java", required = true)
    private File testSourceDirectory;

    /**
     * Location of the production source directory.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/java", required = true)
    private File productionSourceDirectory;

    public void execute() throws MojoExecutionException {
        // Verificar que el directorio de pruebas existe
        if (!testSourceDirectory.exists() || !testSourceDirectory.isDirectory()) {
            throw new MojoExecutionException("El directorio de pruebas no existe: " + testSourceDirectory.getAbsolutePath());
        }
        
        // Buscar archivos de prueba de manera recursiva y en todosl os posibles paquetes existentes
        List<File> testFiles = listTestFilesRecursively(testSourceDirectory);
        List<File> prodFiles = new ArrayList<File>();
        
        //Por cada testFile busco su correspondiente prodFile
        for(File testFile : testFiles) {
        	File prodFile = lookForProdFile(productionSourceDirectory, testFile);
        	
        	if(prodFile != null && prodFile.isFile()) {
        		getLog().warn("Found prodFile: "+prodFile.getAbsolutePath()+ " Para testfile: " + testFile.getAbsolutePath());
        	} else {
        		getLog().warn("Not Found prodFile Para testfile: " + testFile.getAbsolutePath());
        		
        	}
        	
        	prodFiles.add(prodFile);
        	
        }
       
        getLog().info("1: " + testFiles.size() + "2: " + prodFiles.size());
        getLog().info("*** TEST SMELLS REPORT ***");

        TestSmellDetector testSmellDetector = new TestSmellDetector(new DefaultThresholds());

        String line;

        List<String> columnNames;
        List<String> columnValues;

        columnNames = testSmellDetector.getTestSmellNames();
        columnNames.add(0, "App");
        columnNames.add(1, "TestClass");
        columnNames.add(2, "TestFilePath");
        columnNames.add(3, "ProductionFilePath");
        columnNames.add(4, "RelativeTestFilePath");
        columnNames.add(5, "RelativeProductionFilePath");
        columnNames.add(6, "NumberOfMethods");

        line = String.join(",", columnNames);
        System.out.println(line);

        try {            
        	List<TestFile> testFilesArray = new ArrayList<>();
            
            for(int i = 0; i < testFiles.size(); i++) {
            	TestFile testFile;
            	
            	if(prodFiles.get(i) == null) {
            		testFile = new TestFile("testSmellsReport", testFiles.get(i).getAbsolutePath(), "");
            	} else {
            		testFile = new TestFile("testSmellsReport", testFiles.get(i).getAbsolutePath(), prodFiles.get(i).getAbsolutePath());
            	}
            	
            	testFilesArray.add(testFile);
            }
            
            /*
               Iterate through all test files to detect smells and then write the output
            */
            TestFile tempFile;
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date;

            // Save original System.out
            PrintStream originalOut = System.out;

            // Create a new PrintStream that does nothing
            PrintStream noOpStream = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                    // Do nothing
                }
            });

            for (TestFile file : testFilesArray) {

                // Redirect System.out to the no-op PrintStream
              //  System.setOut(noOpStream);
                //detect smells
                tempFile = testSmellDetector.detectSmells(file);
                // Restore original System.out
            //    System.setOut(originalOut);

                //write output
                columnValues = new ArrayList<>();
                columnValues.add(file.getApp());
                columnValues.add(file.getTestFileName());
                columnValues.add(file.getTestFilePath());
                columnValues.add(file.getProductionFilePath());
                columnValues.add(file.getRelativeTestFilePath());
                columnValues.add(file.getRelativeProductionFilePath());
                columnValues.add(String.valueOf(file.getNumberOfTestMethods()));
                for (AbstractSmell smell : tempFile.getTestSmells()) {
                    try {
                        columnValues.add(String.valueOf(smell.getNumberOfSmellyTests()));
                    } catch (NullPointerException e) {
                        columnValues.add("");
                    }
                }
                line += "," + columnValues;
                System.out.println(line);
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
    }
     

    private File lookForProdFile(File productionSourceDirectory, File testFile){
    	File[] files = productionSourceDirectory.listFiles();
    	File returnFile = null;
    	
    	for(File file : files) {
    		if(file.isDirectory()) {
    			File result = lookForProdFile(file, testFile);
    			
    			if(result != null) {
    				returnFile = result;
    				break;
    			}
    			
    		} else if(file.isFile()) {
    			String name = testFile.getName().replace("Test.java", ".java");
    			if(name.equals(file.getName())) {
    				returnFile = file;
    				break;
    				
    			}
    		}
    	}
    	
    	return returnFile;
    }
    
    // Método para listar archivos de prueba de manera recursiva
    private List<File> listTestFilesRecursively(File directory) throws MojoExecutionException {
        List<File> testFiles = new ArrayList<>();

        listTestFilesRecursively(directory, testFiles);
        
        return testFiles;
    }
    
    
    private void listTestFilesRecursively(File directory, List<File> testFiles) {
    	File[] files = directory.listFiles();
    	
    	for (File file : files) {
            if (file.isDirectory()) {
                // Si es un directorio, buscar archivos dentro de él recursivamente
            	listTestFilesRecursively(file, testFiles);
            } else if (file.isFile() && file.getName().endsWith("Test.java")) {
                    getLog().info("Archivo test encontrado: " + file.getName());
                	testFiles.add(file);
            }
        }
    }

}