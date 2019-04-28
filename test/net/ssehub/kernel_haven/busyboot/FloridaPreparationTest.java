/*
 * Copyright 2018-2019 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ssehub.kernel_haven.busyboot;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.Util;

/**
 * Tests the {@link FloridaPreparation}.
 * 
 * @author Adam
 */
@SuppressWarnings("null")
public class FloridaPreparationTest {
    
    private static final File TESTDATA = new File("testdata/florida");
    
    private static final File OUT_FOLDER = new File(TESTDATA, "tmpOut");
    
    /**
     * Wipes the {@link #OUT_FOLDER} for testing.
     * 
     * @throws IOException If wiping fails. 
     */
    @Before
    public void setUp() throws IOException {
        if (OUT_FOLDER.exists()) {
            Util.deleteFolder(OUT_FOLDER);
        }
        
        OUT_FOLDER.mkdirs();
    }
    
    /**
     * Removes the {@link #OUT_FOLDER}.
     */
    @After
    public void tearDown() {
        try {
            Util.deleteFolder(OUT_FOLDER);
        } catch (IOException e) {
            // ignore...
        }
    }
    
    /**
     * Tests simple replacements.
     * 
     * @throws SetUpException unwanted.
     * @throws IOException unwanted.
     */
    @Test
    public void testSimpleReplacements() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        File target = new File(TESTDATA, "simpleReplacements");
        prep.prepare(target, OUT_FOLDER);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));
        
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(OUT_FOLDER, "test.c")))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 3) {
                    assertThat(line, is("#if defined(calc)"));
                } else if (in.getLineNumber() == 9) {
                    assertThat(line, is("#endif // calc"));
                } else if (in.getLineNumber() == 14) {
                    assertThat(line, is("#if defined(use_calc)"));
                } else if (in.getLineNumber() == 16) {
                    assertThat(line, is("#endif // use_calc"));
                    
                } else if (in.getLineNumber() == 5) {
                    assertThat(line, is("int calc(int a, int b) {"));
                } else if (in.getLineNumber() == 6) {
                    assertThat(line, is("\treturn a + b;"));
                } else if (in.getLineNumber() == 7) {
                    assertThat(line, is("}"));
                    
                } else if (in.getLineNumber() == 15) {
                    assertThat(line, is("\ttoPrint = calc(5, 4);"));
                } else if (in.getLineNumber() == 18) {
                    assertThat(line, is("\tprintf(\"%d\\n\", toPrint);"));
                }
                
            }
            
        }
    }
    
    /**
     * Tests simple replacements in a folder structure with sub-folders.
     * 
     * @throws IOException unwanted.
     * @throws SetUpException unwanted.
     */
    @Test
    public void testSimpleReplacementsWithSubFolders() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        File target = new File(TESTDATA, "subfolders");
        prep.prepare(target, OUT_FOLDER);
        
        File fileA = new File(OUT_FOLDER, "file_a.c");
        File dir1 = new File(OUT_FOLDER, "sub-folder");
        File fileB = new File(dir1, "file_b.c");
        File dir2 = new File(dir1, "sub-sub-folder");
        File fileC = new File(dir2, "file_c.c");
        
        assertThat(fileA.isFile(), is(true));
        assertThat(fileB.isFile(), is(true));
        assertThat(fileC.isFile(), is(true));
        
        assertThat(OUT_FOLDER.listFiles().length, is(2));
        assertThat(dir1.isDirectory(), is(true));
        assertThat(dir1.listFiles().length, is(2));
        assertThat(dir2.isDirectory(), is(true));
        assertThat(dir2.listFiles().length, is(1));
        
        try (LineNumberReader in = new LineNumberReader(new FileReader(fileA))) {
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("#if defined(Feature_A)"));
                } else if (in.getLineNumber() == 2) {
                    assertThat(line, is("    someCode();"));
                } else if (in.getLineNumber() == 3) {
                    assertThat(line, is("#endif // Feature_A"));
                } else {
                    fail("Invalid line number: " + in.getLineNumber());
                }
            }
        }
        try (LineNumberReader in = new LineNumberReader(new FileReader(fileB))) {
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("#if defined(Feature_B)"));
                } else if (in.getLineNumber() == 2) {
                    assertThat(line, is("    someCode();"));
                } else if (in.getLineNumber() == 3) {
                    assertThat(line, is("#endif // Feature_B"));
                } else {
                    fail("Invalid line number: " + in.getLineNumber());
                }
            }
        }
        try (LineNumberReader in = new LineNumberReader(new FileReader(fileC))) {
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("#if defined(Feature_C)"));
                } else if (in.getLineNumber() == 2) {
                    assertThat(line, is("    someCode();"));
                } else if (in.getLineNumber() == 3) {
                    assertThat(line, is("#endif // Feature_C"));
                } else {
                    fail("Invalid line number: " + in.getLineNumber());
                }
            }
        }
    }
    
    /**
     * Tests that a non-C source file will be copied as-is.
     * 
     * @throws IOException unwanted.
     * @throws SetUpException unwanted.
     */
    @Test
    public void testNonCFile() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        File target = new File(TESTDATA, "nonSourceFile");
        prep.prepare(target, OUT_FOLDER);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.txt")}));
        
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(OUT_FOLDER, "test.txt")))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("//&Line[Feature_A]"));
                } else if (in.getLineNumber() == 2) {
                    assertThat(line, is("    someCode();"));
                } else {
                    fail("Invalid line number: " + in.getLineNumber());
                }
            }
            
        }
    }
    
    /**
     * Tests simple replacements with other C preprocessor statements in the file.
     * 
     * @throws SetUpException unwanted.
     * @throws IOException unwanted.
     */
    @Test
    public void testSimpleReplacementsWithCpp() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        File target = new File(TESTDATA, "simpleReplacementsWithCpp");
        prep.prepare(target, OUT_FOLDER);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));
        
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(OUT_FOLDER, "test.c")))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 5) {
                    assertThat(line, is("#if defined(calc)"));
                } else if (in.getLineNumber() == 11) {
                    assertThat(line, is("#endif // calc"));
                } else if (in.getLineNumber() == 20) {
                    assertThat(line, is("#if defined(use_calc)"));
                } else if (in.getLineNumber() == 22) {
                    assertThat(line, is("#endif // use_calc"));
                    
                } else if (in.getLineNumber() == 7) {
                    assertThat(line, is("int calc(int a, int b) {"));
                } else if (in.getLineNumber() == 8) {
                    assertThat(line, is("\treturn a + b;"));
                } else if (in.getLineNumber() == 9) {
                    assertThat(line, is("}"));
                    
                } else if (in.getLineNumber() == 21) {
                    assertThat(line, is("\ttoPrint = calc(5, 4);"));
                } else if (in.getLineNumber() == 24) {
                    assertThat(line, is("\tprintf(\"%d\\n\", toPrint);"));
                    
                } else if (in.getLineNumber() == 3) {
                    assertThat(line, is("#ifdef NOT_EMPTY"));
                } else if (in.getLineNumber() == 13) {
                    assertThat(line, is("#endif"));
                } else if (in.getLineNumber() == 18) {
                    assertThat(line, is("#if defined(NOT_EMPTY)"));
                } else if (in.getLineNumber() == 26) {
                    assertThat(line, is("#endif"));
                }
                
            }
            
        }
    }
    
    /**
     * Tests that a correct warning is printed when the nesting of C preprocessor and FLOrIDA blocks is wrong.
     * 
     * @throws IOException unwanted.
     * @throws SetUpException unwanted.
     */
    @Test
    public void testNestingOfCppAndPvWrong() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Logger.get().addTarget(log);
        
        File target = new File(TESTDATA, "nestingOfCppAndFloridaWrong");
        File targetFile = new File(target, "test.c");
        prep.prepare(target, OUT_FOLDER);
        
        Logger.get().removeTarget(Logger.get().getTargets().size() - 1);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));

        String[] lines = log.toString().split("\n");
        assertThat(lines[0], endsWith("CppBlock in " + targetFile
                + " in line 1 has 1 opening and 0 closing FLOrIDA statements"));
        assertThat(lines[1], endsWith("CppBlock in " + targetFile
                + " in line 5 has a closing FLOrIDA statement without a prior opening one"));
        assertThat(lines[2], endsWith("CppBlock in " + targetFile
                + " in line 5 has 0 opening and 1 closing FLOrIDA statements"));
        
        assertThat(lines.length, is(3));
    }
    
    /**
     * Tests that a correct warning is printed when the nesting of C preprocessor and FLOrIDA blocks is wrong.
     * 
     * @throws IOException unwanted.
     * @throws SetUpException unwanted.
     */
    @Test
    public void testNestingOfCppAndPvWrong2() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Logger.get().addTarget(log);

        File target = new File(TESTDATA, "nestingOfCppAndFloridaWrong2");
        File targetFile = new File(target, "test.c");
        prep.prepare(target, OUT_FOLDER);
        
        Logger.get().removeTarget(Logger.get().getTargets().size() - 1);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));

        String[] lines = log.toString().split("\n");
        assertThat(lines[0], endsWith("CppBlock in " + targetFile
                + " in line 3 has a closing FLOrIDA statement without a prior opening one"));
        
        assertThat(lines.length, is(1));
    }
    
    /**
     * Tests that a correct warning is printed when a FLOrIDA condition can not be replaced.
     * 
     * @throws IOException unwanted.
     * @throws SetUpException unwanted.
     */
    @Test
    public void testWrongFloridaCondition() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Logger.get().addTarget(log);
        
        File target = new File(TESTDATA, "wrongFloridaCondition");
        File targetFile = new File(target, "test.c");
        prep.prepare(target, OUT_FOLDER);
        
        Logger.get().removeTarget(Logger.get().getTargets().size() - 1);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(OUT_FOLDER, "test.c")))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("// Error replacing FLOrIDA condition: //&SomethingWrong"));
                }
            }
        }

        String[] lines = log.toString().split("\n");
        assertThat(lines[0], endsWith("Unknown FLOrIDA condition in " + targetFile + " in line 1:"));
        assertThat(lines[1], endsWith("SomethingWrong"));
        
        assertThat(lines.length, is(2));
    }
    
    /**
     * Tests that the converter does not crash when an #endif appears without a previous #if.
     * 
     * @throws IOException unwanted.
     * @throws SetUpException unwanted.
     */
    @Test
    public void wrongCppStructure() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Logger.get().addTarget(log);
        
        File target = new File(TESTDATA, "wrongCppStructure"); 
        prep.prepare(target, OUT_FOLDER);
        
        Logger.get().removeTarget(Logger.get().getTargets().size() - 1);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(OUT_FOLDER, "test.c")))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("#endif"));
                }
            }
        }
    }
    
    /**
     * Tests that a not matching begin[] and end[] block log a warning.
     * 
     * @throws IOException unwanted.
     * @throws SetUpException unwanted.
     */
    @Test
    public void testNotMatchingBeginAndEnd() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Logger.get().addTarget(log);
        
        File target = new File(TESTDATA, "notMatching");
        File targetFile = new File(target, "test.c");
        prep.prepare(target, OUT_FOLDER);
        
        Logger.get().removeTarget(Logger.get().getTargets().size() - 1);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(OUT_FOLDER, "test.c")))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("#if defined(FEATURE_A)"));
                } else if (in.getLineNumber() == 2) {
                    assertThat(line, is("#endif // FEATURE_B"));
                }
            }
        }

        String[] lines = log.toString().split("\n");
        assertThat(lines[0], endsWith("begin[] and end[] block features don't match in " + targetFile + " in line 2"));
        assertThat(lines[1], endsWith("Got //&end[FEATURE_B], expected //&end[FEATURE_A]"));
        
        assertThat(lines.length, is(2));
    }
    
    /**
     * Tests that the following invalid combination is handled gracefully.
     * 
     * <pre>{@code
     * //&Line[feature]
     *    someCode();
     * //&end[feature]
     * }</pre>
     * 
     * @throws IOException unwanted.
     * @throws SetUpException unwanted.
     */
    @Test
    public void testLineWithEnd() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Logger.get().addTarget(log);
        
        File target = new File(TESTDATA, "lineWithEnd");
        File targetFile = new File(target, "test.c");
        prep.prepare(target, OUT_FOLDER);
        
        Logger.get().removeTarget(Logger.get().getTargets().size() - 1);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(OUT_FOLDER, "test.c")))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("#if defined(Feature_A)"));
                } else if (in.getLineNumber() == 2) {
                    assertThat(line, is("    someCode();"));
                } else if (in.getLineNumber() == 3) {
                    assertThat(line, is("#endif // Feature_A"));
                } else if (in.getLineNumber() == 4) {
                    assertThat(line, is("#endif // Feature_A"));
                }
            }
        }

        String[] lines = log.toString().split("\n");
        
        assertThat(lines[0], endsWith(targetFile
                + " in line 3 has a closing FLOrIDA statement without a prior opening one"));
        
        assertThat(lines.length, is(1));
    }
    
    /**
     * Tests that the case of FLOrIDA blocks doesn't matter.
     * 
     * @throws SetUpException unwanted.
     * @throws IOException unwanted.
     */
    @Test
    public void testsIgnoreCase() throws IOException, SetUpException {
        FloridaPreparation prep = new FloridaPreparation();
        
        File target = new File(TESTDATA, "ignoreCase");
        prep.prepare(target, OUT_FOLDER);
        
        assertThat(OUT_FOLDER.listFiles(), is(new File[] {new File(OUT_FOLDER, "test.c")}));
        
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(OUT_FOLDER, "test.c")))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                
                if (in.getLineNumber() == 1) {
                    assertThat(line, is("#if defined(Feature_A)"));
                } else if (in.getLineNumber() == 2) {
                    assertThat(line, is("    someCode();"));
                } else if (in.getLineNumber() == 3) {
                    assertThat(line, is("#endif // Feature_A"));
                } else if (in.getLineNumber() == 4) {
                    assertThat(line, is(""));
                } else if (in.getLineNumber() == 5) {
                    assertThat(line, is("#if defined(Feature_B)"));
                } else if (in.getLineNumber() == 6) {
                    assertThat(line, is("    someOtherCode();"));
                } else if (in.getLineNumber() == 7) {
                    assertThat(line, is("#endif // Feature_B"));
                }
                
            }
            
        }
    }
    
}
