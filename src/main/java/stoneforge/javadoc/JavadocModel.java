/*
 * Copyright (C) 2020 stoneforge Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package stoneforge.javadoc;

import static javax.tools.DocumentationTool.Location.DOCUMENTATION_OUTPUT;
import static javax.tools.StandardLocation.*;

import java.awt.Desktop;
import java.io.File;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.DiagnosticListener;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import icy.manipulator.Icy;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import kiss.I;
import kiss.Variable;
import kiss.XML;
import psychopath.Directory;
import psychopath.Locator;
import stoneforge.SiteBuilder;
import stoneforge.javadoc.analyze.ClassInfo;
import stoneforge.javadoc.analyze.Data;
import stoneforge.javadoc.analyze.MethodInfo;
import stoneforge.javadoc.analyze.TypeResolver;
import stoneforge.javadoc.analyze.Util;
import stylist.StyleDeclarable;
import stylist.Stylist;

@Icy
public abstract class JavadocModel {

    /** The default JDK API's location. */
    public static final String JDK = "https://docs.oracle.com/en/java/javase/16/docs/api/";

    /** The scanned data. */
    public final Data data = new Data();

    private boolean collectingSample = false;

    /** MethodID-SampleCode pari. */
    private Map<String, String> samples = new HashMap();

    /** PackageName-URL pair. */
    private final Map<String, String> externals = new HashMap();

    /** The internal pacakage names. */
    private final Set<String> internals = new HashSet();

    /**
     * The list of source directories.
     * 
     * @return
     */
    @Icy.Property
    public abstract List<Directory> sources();

    /**
     * The list of source directories.
     * 
     * @return
     */
    @Icy.Overload("sources")
    private List<Directory> sources(String... paths) {
        return I.signal(paths).map(Locator::directory).toList();
    }

    /**
     * The list of source directories.
     * 
     * @return
     */
    @Icy.Overload("sources")
    private List<Directory> sources(Path... paths) {
        return I.signal(paths).map(Locator::directory).toList();
    }

    /**
     * The list of source directories.
     * 
     * @return
     */
    @Icy.Property
    public List<psychopath.Location> classpath() {
        return List.of();
    }

    /**
     * The list of source directories.
     * 
     * @return
     */
    @Icy.Overload("classpath")
    private List<psychopath.Location> classpath(String... paths) {
        return I.signal(paths).map(Locator::locate).toList();
    }

    /**
     * The list of source directories.
     * 
     * @return
     */
    @Icy.Overload("classpath")
    private List<psychopath.Location> classpath(Path... paths) {
        return I.signal(paths).map(Locator::locate).toList();
    }

    /**
     * Specify the directory where the product is output.
     * 
     * @return
     */
    @Icy.Property(nullable = true)
    public abstract Directory output();

    /**
     * Specify the directory where the product is output.
     * 
     * @return
     */
    @Icy.Overload("output")
    private Directory output(String path) {
        return Locator.directory(path);
    }

    /**
     * Specify the directory where the product is output.
     * 
     * @return
     */
    @Icy.Overload("output")
    private Directory output(Path path) {
        return Locator.directory(path);
    }

    /**
     * The product name.
     * 
     * @return
     */
    @Icy.Property
    public abstract String product();

    /**
     * The project name.
     * 
     * @return
     */
    @Icy.Property
    public abstract String project();

    /**
     * The product version.
     * 
     * @return
     */
    @Icy.Property
    public abstract String version();

    /**
     * Specify the directory of samples.
     * 
     * @return
     */
    @Icy.Property
    public Directory sample() {
        return null;
    }

    /**
     * Specify the directory of samples.
     * 
     * @return
     */
    @Icy.Overload("sample")
    private Directory sample(String path) {
        return Locator.directory(path);
    }

    /**
     * Specify the directory of samples.
     * 
     * @return
     */
    @Icy.Overload("sample")
    private Directory sample(Path path) {
        return Locator.directory(path);
    }

    /**
     * Use JDK as the resolvable external document.
     * 
     * @return
     */
    public final Javadoc useExternalJDKDoc() {
        return useExternalDoc(JDK);
    }

    /**
     * Specifies the URL of the resolvable external document.
     * 
     * @param urls A list of document URL．
     * @return Chainable API.
     */
    public final Javadoc useExternalDoc(String... urls) {
        if (urls != null) {
            for (String url : urls) {
                if (url != null && url.startsWith("http") && url.endsWith("/api/")) {
                    I.http(url + "overview-tree.html", XML.class)
                            .retryWhen(e -> e.delay(200, TimeUnit.MILLISECONDS).take(20))
                            .flatIterable(xml -> xml.find(".horizontal a"))
                            .waitForTerminate()
                            .to(xml -> {
                                externals.put(xml.text(), url);
                            });
                }
            }
        }
        return (Javadoc) this;
    }

    /**
     * Generate documents.
     */
    public final Javadoc build() {
        synchronized (JavadocModel.class) {
            Internal.model = this;

            DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
            DiagnosticListener<JavaFileObject> listener = o -> {
                System.out.println(o);
            };

            // ========================================================
            // Collect sample source
            // ========================================================
            if (sample() != null) {
                collectingSample = true;

                try (StandardJavaFileManager m = tool.getStandardFileManager(listener, Locale.getDefault(), Charset.defaultCharset())) {
                    m.setLocation(SOURCE_PATH, I.signal(sources()).startWith(sample()).map(Directory::asJavaFile).toList());
                    m.setLocation(CLASS_PATH, classpath().stream().map(psychopath.Location::asJavaFile).collect(Collectors.toList()));

                    tool.getTask(null, m, listener, Internal.class, List.of("-package"), I
                            .signal(m.list(SOURCE_PATH, "", Set.of(Kind.SOURCE), true))
                            .take(o -> o.getName().startsWith(sample().toString()) && o.getName().endsWith("Test.java"))
                            .toList()).call();
                } catch (Throwable e) {
                    throw I.quiet(e);
                } finally {
                    collectingSample = false;
                }
            }

            // ========================================================
            // Scan javadoc from main source
            // ========================================================
            try (StandardJavaFileManager manager = tool.getStandardFileManager(listener, Locale.getDefault(), Charset.defaultCharset())) {
                manager.setLocationFromPaths(SOURCE_PATH, sources().stream().map(Directory::asJavaPath).collect(Collectors.toList()));
                manager.setLocationFromPaths(DOCUMENTATION_OUTPUT, List
                        .of(output() == null ? Path.of("") : output().create().asJavaPath()));
                // manager.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath().stream()
                // .map(psychopath.Location::asJavaPath)
                // .collect(Collectors.toList()));

                Iterable<? extends JavaFileObject> units = manager.list(SOURCE_PATH, "", Set.of(Kind.SOURCE), true);
                if (tool.getTask(null, manager, listener, Internal.class, List.of(), units).call()) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return (Javadoc) this;
    }

    /**
     * Build {@link Doclet} to generate documents.
     * 
     * @return
     */
    public final Class<? extends Doclet> buildDocletClass() {
        Internal.model = this;

        return Internal.class;
    }

    /**
     * Show the generated document in your browser.
     * 
     * @return
     */
    public final Javadoc show() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(9321), 0);
            server.createContext("/", context -> {
                psychopath.File file = output().file(context.getRequestURI().getPath().substring(1));
                byte[] body = file.text().getBytes(StandardCharsets.UTF_8);

                // header
                Headers headers = context.getResponseHeaders();
                headers.set("Content-Type", mime(file));
                context.sendResponseHeaders(200, body.length);

                // body
                OutputStream out = context.getResponseBody();
                out.write(body);
                out.flush();
                out.close();
            });
            server.start();
        } catch (BindException e) {
            // already launched
        } catch (Exception e) {
            throw I.quiet(e);
        }

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI("http://localhost:9321/javadoc.html"));
            } catch (Exception e) {
                throw I.quiet(e);
            }
        }

        return (Javadoc) this;
    }

    /**
     * Detect mime-type.
     * 
     * @param file
     * @return
     */
    private String mime(psychopath.File file) {
        switch (file.extension()) {
        case "css":
            return "text/css";
        case "js":
            return "application/javascript";
        case "html":
            return "text/html";
        default:
            return "text/plain";
        }
    }

    /**
     * <h>DONT USE THIS CLASS</h>
     * <p>
     * It is a Doclet for internal use, but it is public because it cannot be made private due to
     * the specifications of the documentation tool.
     * </p>
     */
    public static class Internal implements Doclet {

        /** The setting model. */
        private static JavadocModel model;

        /**
         * {@inheritDoc}
         */
        @Override
        public final void init(Locale locale, Reporter reporter) {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean run(DocletEnvironment env) {
            Util.DocUtils = env.getDocTrees();
            Util.ElementUtils = env.getElementUtils();
            Util.TypeUtils = env.getTypeUtils();

            try {
                model.initialize();

                for (Element element : env.getSpecifiedElements()) {
                    switch (element.getKind()) {
                    case MODULE:
                        model.process((ModuleElement) element);
                        break;

                    case PACKAGE:
                        model.process((PackageElement) element);
                        break;

                    default:
                        model.process((TypeElement) element);
                        break;
                    }
                }
            } finally {
                model.complete();
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final String getName() {
            return getClass().getSimpleName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final Set<? extends Option> getSupportedOptions() {
            return Set.of();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }
    }

    /**
     * Find class by its name.
     * 
     * @param className
     */
    final Variable<ClassInfo> findByClassName(String className) {
        for (ClassInfo info : data.types) {
            if ((info.packageName + "." + info.name).equals(className)) {
                return Variable.of(info);
            }
        }
        return Variable.empty();
    }

    /**
     * Find all package names in the source directory.
     * 
     * @return
     */
    final Set<String> findSourcePackages() {
        // collect internal package names
        Set<String> packages = new HashSet();

        I.signal(sources()).flatMap(Directory::walkDirectoryWithBase).to(sub -> {
            packages.add(sub.ⅰ.relativize(sub.ⅱ).toString().replace(File.separatorChar, '.'));
        });
        return packages;
    }

    /**
     * Initialization phase.
     */
    private void initialize() {
        internals.addAll(findSourcePackages());
    }

    /**
     * Process a class or interface program element. Provides access to information about the type
     * and its members. Note that an enum type is a kind of class and an annotation type is a kind
     * of interface.
     * 
     * @param root A class or interface program element root.
     */
    private void process(TypeElement root) {
        ClassInfo info = new ClassInfo(root, new TypeResolver(externals, internals, root), null);
        if (!collectingSample) {
            data.add(info);
        } else {
            for (MethodInfo method : info.methods()) {
                List<String[]> refers = method.referenceBySee();
                if (!refers.isEmpty()) {
                    String code = Util.getSourceCode(method);
                    for (String[] ref : refers) {
                        String r = ref[0] + ref[1];
                        samples.put(r, code);
                    }
                }
            }
        }
    }

    /**
     * Process a package program element. Provides access to information about the package and its
     * members.
     * 
     * @param root A package program element root.
     */
    private void process(PackageElement root) {
    }

    /**
     * Process a module program element. Provides access to information about the module, its
     * directives, and its members.
     * 
     * @param root A module program element root.
     */
    private void process(ModuleElement root) {
    }

    /**
     * Completion phase.
     */
    private void complete() {
        // sort data
        data.modules.sort(Comparator.naturalOrder());
        data.packages.sort(Comparator.naturalOrder());
        data.types.sort(Comparator.naturalOrder());

        // after care
        data.connectSubType();

        if (output() != null) {
            SiteBuilder site = SiteBuilder.root(output()).guard("index.html", "main.js", "main.css");

            // build CSS
            I.load(JavadocModel.class);
            Stylist.pretty()
                    .importNormalizeStyle()
                    .styles(I.findAs(StyleDeclarable.class))
                    .formatTo(output().file("main.css").asJavaPath());

            // build HTML
            site.buildHTML("javadoc.html", new MainPage(this, null));

            for (ClassInfo info : data.types) {
                site.buildHTML("types/" + info.packageName + "." + info.name + ".html", new MainPage(this, info));
            }
        }
    }
}