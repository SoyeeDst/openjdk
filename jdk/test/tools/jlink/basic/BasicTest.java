/**
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Basic test of jlink to create jmods and images
 * @author Andrei Eremeev
 * @library /lib/testlibrary
 * @modules java.base/jdk.internal.module
 *          jdk.jlink
 *          jdk.compiler
 * @build jdk.testlibrary.ProcessTools
 *        jdk.testlibrary.OutputAnalyzer
 *        JarUtils CompilerUtils
 * @run main BasicTest
 */

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.spi.ToolProvider;

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;

public class BasicTest {
    static final ToolProvider JMOD_TOOL = ToolProvider.findFirst("jmod")
        .orElseThrow(() ->
            new RuntimeException("jmod tool not found")
        );

    static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
        .orElseThrow(() ->
            new RuntimeException("jlink tool not found")
        );

    private final Path jdkHome = Paths.get(System.getProperty("test.jdk"));
    private final Path jdkMods = jdkHome.resolve("jmods");
    private final Path testSrc = Paths.get(System.getProperty("test.src"));
    private final Path src = testSrc.resolve("src");
    private final Path classes = Paths.get("classes");
    private final Path jmods = Paths.get("jmods");
    private final Path jars = Paths.get("jars");

    public static void main(String[] args) throws Throwable {
        new BasicTest().run();
    }

    public void run() throws Throwable {
        if (Files.notExists(jdkMods)) {
            return;
        }

        if (!CompilerUtils.compile(src, classes)) {
            throw new AssertionError("Compilation failure. See log.");
        }

        String modName = "test";
        Files.createDirectories(jmods);
        Files.createDirectories(jars);
        Path jarfile = jars.resolve("test.jar");
        JarUtils.createJarFile(jarfile, classes);

        Path image = Paths.get("mysmallimage");
        runJmod(jarfile.toString(), modName);
        runJlink(image, modName, "--compress", "2");
        execute(image, modName);

        Files.delete(jmods.resolve(modName + ".jmod"));

        image = Paths.get("myimage");
        runJmod(classes.toString(), modName);
        runJlink(image, modName);
        execute(image, modName);
    }

    private void execute(Path image, String moduleName) throws Throwable {
        String cmd = image.resolve("bin").resolve(moduleName).toString();
        OutputAnalyzer analyzer;
        if (System.getProperty("os.name").startsWith("Windows")) {
            analyzer = ProcessTools.executeProcess("sh.exe", cmd, "1", "2", "3");
        } else {
            analyzer = ProcessTools.executeProcess(cmd, "1", "2", "3");
        }
        if (analyzer.getExitValue() != 0) {
            throw new AssertionError("Image invocation failed: rc=" + analyzer.getExitValue());
        }
    }

    private void runJlink(Path image, String modName, String... options) {
        List<String> args = new ArrayList<>();
        Collections.addAll(args,
                "--module-path", jdkMods + File.pathSeparator + jmods,
                "--add-modules", modName,
                "--output", image.toString());
        Collections.addAll(args, options);

        PrintWriter pw = new PrintWriter(System.out);
        int rc = JLINK_TOOL.run(pw, pw, args.toArray(new String[args.size()]));
        if (rc != 0) {
            throw new AssertionError("Jlink failed: rc = " + rc);
        }
    }

    private void runJmod(String cp, String modName) {
        int rc = JMOD_TOOL.run(System.out, System.out, new String[] {
                "create",
                "--class-path", cp,
                "--module-version", "1.0",
                "--main-class", "jdk.test.Test",
                jmods.resolve(modName + ".jmod").toString(),
        });
        if (rc != 0) {
            throw new AssertionError("Jmod failed: rc = " + rc);
        }
    }
}
