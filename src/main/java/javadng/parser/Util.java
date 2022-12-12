/*
 * Copyright (C) 2020 stoneforge Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package javadng.parser;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocSourcePositions;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;

import kiss.I;
import psychopath.Directory;
import psychopath.File;

public final class Util {

    static {
        StaticJavaParser.getConfiguration().setLanguageLevel(LanguageLevel.CURRENT);
    }

    /** Guilty Accessor. */
    public static DocTrees DocUtils;

    /** Guilty Accessor. */
    public static Elements ElementUtils;

    /** Guilty Accessor. */
    public static Types TypeUtils;

    /** Guilty Accessor. */
    public static List<Directory> Samples;

    /**
     * Find the top-level {@link TypeElement} (not member class).
     * 
     * @param e
     * @return
     */
    public static TypeElement getTopLevelTypeElement(Element e) {
        Element parent = e.getEnclosingElement();

        while (parent != null && parent.getKind() != ElementKind.PACKAGE) {
            e = parent;
            parent = e.getEnclosingElement();
        }
        return (TypeElement) e;
    }

    /**
     * Get the document line numbers of the specified {@link Element}.
     * 
     * @param e
     * @return
     */
    public static int[] getDocumentLineNumbers(Element e) {
        try {
            DocSourcePositions positions = DocUtils.getSourcePositions();

            TreePath path = Util.DocUtils.getPath(e);
            CompilationUnitTree cut = path.getCompilationUnit();

            DocCommentTree tree = Util.DocUtils.getDocCommentTree(e);
            int start = (int) positions.getStartPosition(cut, tree, tree);
            int end = (int) positions.getEndPosition(cut, tree, tree);

            int[] lines = {1, 1};
            CharSequence chars = cut.getSourceFile().getCharContent(true);
            for (int i = 0; i < end; i++) {
                if (i == start) {
                    lines[0] = lines[1];
                }
                if (chars.charAt(i) == '\n') lines[1]++;
            }

            return lines;
        } catch (IOException x) {
            throw I.quiet(x);
        }
    }

    /**
     * Get the source code of the specified class.
     */
    public static String getSourceCode(String fqcn, String memberDescriptor) {
        try {
            for (Directory sample : Samples) {
                List<String> split = List.of(fqcn.split("\\."));
                int max = split.size();
                int current = max;
                while (0 < current) {
                    File file = sample.file(split.subList(0, current--).stream().collect(Collectors.joining("/", "", ".java")));
                    if (file.isPresent()) {
                        if (current + 1 != max) {
                            memberDescriptor = split.subList(current + 1, max).stream().collect(Collectors.joining("."));
                        }

                        CompilationUnit parsed = StaticJavaParser.parse(file.asJavaFile());
                        Node node = parsed.findRootNode().removeComment();

                        // remove unnecessary annotations
                        for (MethodDeclaration method : node.findAll(MethodDeclaration.class)) {
                            String[] removables = {"Override", "SuppressWarnings"};
                            for (String removable : removables) {
                                method.getAnnotationByName(removable).ifPresent(AnnotationExpr::remove);
                            }
                        }

                        if (memberDescriptor == null) {
                            return node.toString();
                        } else {
                            for (MethodDeclaration method : parsed.findAll(MethodDeclaration.class)) {
                                if (method.getSignature().asString().equals(memberDescriptor)) {
                                    return readCode(file, method);
                                }
                            }

                            for (ClassOrInterfaceDeclaration type : parsed.findAll(ClassOrInterfaceDeclaration.class)) {
                                if (type.getNameAsString().equals(memberDescriptor)) {
                                    return readCode(file, type);
                                }
                            }

                            for (FieldDeclaration field : parsed.findAll(FieldDeclaration.class)) {
                                for (VariableDeclarator variable : field.findAll(VariableDeclarator.class)) {
                                    if (variable.getNameAsString().equals(memberDescriptor)) {
                                        return readCode(file, field);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw I.quiet(e);
        }
        return "";
    }

    /**
     * Get the source code from source file.
     * 
     * @param file
     * @param node
     * @return
     */
    private static String readCode(File file, Node node) {
        int start = node.getBegin().get().line;
        int end = node.getEnd().get().line;
        if (node instanceof FieldDeclaration) start--;
        String code = file.lines().skip(start).take(end - start).scan(Collectors.joining("\r\n")).to().exact();
        return stripHeaderWhitespace(code);
    }

    /**
     * Get the source code of the specified {@link Element}.
     * 
     * @param doc
     * @return
     */
    public static String getSourceCode(DocumentInfo doc) {
        return getSourceCode(doc.e);
    }

    public static String getSourceCode(Element type, String memberDescriptor) {
        if (memberDescriptor.charAt(0) == '#') {
            memberDescriptor = memberDescriptor.substring(1);
        }

        if (type.toString().equals(memberDescriptor)) {
            return getSourceCode(type);
        }

        for (Element e : type.getEnclosedElements()) {
            if (e.toString().equals(memberDescriptor)) {
                return getSourceCode(e);
            }
        }

        return getSourceCode(memberDescriptor, null);
    }

    /**
     * Get the source code of the specified {@link Element}.
     * 
     * @param e
     * @return
     */
    public static String getSourceCode(Element e) {
        try {
            DocSourcePositions positions = DocUtils.getSourcePositions();

            TreePath path = Util.DocUtils.getPath(e);
            CompilationUnitTree cut = path.getCompilationUnit();

            int start = (int) positions.getStartPosition(cut, path.getLeaf());
            int end = (int) positions.getEndPosition(cut, path.getLeaf());
            return stripHeaderWhitespace(cut.getSourceFile().getCharContent(true).subSequence(start, end).toString());
        } catch (IOException error) {
            throw I.quiet(error);
        }
    }

    /**
     * Strip whitespace prettily for the formatted source code.
     * 
     * @param text
     * @return
     */
    public static String stripHeaderWhitespace(String text) {
        List<String> lines = I.list(text.split("\\r\\n|\\r|\\n"));

        if (lines.size() == 1) {
            return text;
        }

        // remove the empty line from head
        ListIterator<String> iter = lines.listIterator();
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.isEmpty()) {
                iter.remove();
            } else {
                break;
            }
        }

        // remove the empty line from tail
        iter = lines.listIterator(lines.size());
        while (iter.hasPrevious()) {
            String line = iter.previous();
            if (line.isEmpty()) {
                iter.remove();
            } else {
                break;
            }
        }

        // remove @Override
        iter = lines.listIterator();
        while (iter.hasNext()) {
            String line = iter.next().trim();
            if (line.equals("@Override") || line.startsWith("@SuppressWarnings") || line.startsWith("@Test")) {
                iter.remove();
            }
        }

        // strip the common width indent
        int indent = lines.stream().mapToInt(Util::countHeaderWhitespace).filter(i -> 0 < i).min().getAsInt();
        return lines.stream().map(line -> stripHeaderWhitespace(line, indent)).collect(Collectors.joining("\r\n"));
    }

    private static int countHeaderWhitespace(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }
        return line.length();
    }

    private static String stripHeaderWhitespace(String line, int size) {
        if (line.length() < size) {
            return line;
        }

        for (int i = 0; i < size; i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return line;
            }
        }
        return line.substring(size);
    }

    /**
     * Collect all types.
     * 
     * @param type
     * @return
     */
    public static Set<TypeMirror>[] getAllTypes(Element type) {
        Set<TypeMirror> supers = new LinkedHashSet();
        Set<TypeMirror> interfaces = new TreeSet<>(Comparator
                .<TypeMirror, String> comparing(t -> ((TypeElement) Util.TypeUtils.asElement(t)).getSimpleName().toString()));
        collect(type.asType(), supers, interfaces);

        return new Set[] {supers, interfaces};
    }

    /**
     * Collect all types.
     * 
     * @param type
     * @param superTypes
     * @param interfaceTypes
     */
    private static void collect(TypeMirror type, Set<TypeMirror> superTypes, Set<TypeMirror> interfaceTypes) {
        for (TypeMirror up : Util.TypeUtils.directSupertypes(type)) {
            if (up.toString().equals("java.lang.Object")) {
                continue;
            }

            Element e = Util.TypeUtils.asElement(up);
            if (e.getKind() == ElementKind.INTERFACE) {
                interfaceTypes.add(up);
            } else {
                superTypes.add(up);
            }
            collect(up, superTypes, interfaceTypes);
        }
    }
}