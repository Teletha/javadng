/*
 * Copyright (C) 2019 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package stoneforge.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import stoneforge.Util;

/**
 * Scanned data repository.
 */
public final class Data {

    /** Type repository. */
    public List<String> modules = new ArrayList();

    /** Type repository. */
    public List<String> packages = new ArrayList();

    /** Type repository. */
    public List<ClassInfo> types = new ArrayList();

    /**
     * Avoid duplication.
     */
    public void add(ClassInfo info) {
        types.add(info);

        if (packages.indexOf(info.packageName) == -1) {
            packages.add(info.packageName);
        }
    }

    /**
     * 
     */
    public void connectSubType() {
        for (ClassInfo type : types) {
            for (Set<TypeMirror> uppers : Util.getAllTypes(type.e)) {
                for (TypeMirror upper : uppers) {
                    Element e = Util.TypeUtils.asElement(upper);
                    for (ClassInfo info : types) {
                        if (info.e.equals(e)) {
                            info.addSub(type);
                            break;
                        }
                    }
                }
            }
        }
    }
}