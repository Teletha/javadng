/*
 * Copyright (C) 2020 stoneforge Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package stoneforge.javadoc.analyze;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import kiss.I;
import kiss.XML;

public abstract class MemberInfo extends DocumentInfo {

    /** The name of this member. */
    public final String name;

    /** The modifier of this member. */
    public final Set<Modifier> modifiers;

    /**
     * @param e
     */
    public MemberInfo(Element e, TypeResolver resolver, DocumentInfo parent) {
        super(e, resolver, parent);

        String name = e.getSimpleName().toString();
        if (name.equals("<init>")) {
            name = e.getEnclosingElement().getSimpleName().toString();
        }

        this.name = name;
        this.modifiers = e.getModifiers();
    }

    /**
     * Check whether this member has static modifier or not.
     * 
     * @return Result.
     */
    public final boolean isStatic() {
        return modifiers.contains(Modifier.STATIC);
    }

    /**
     * Check whether this member is deprecated or not.
     * 
     * @return Result.
     */
    public final boolean isDeprecated() {
        return Util.ElementUtils.isDeprecated(e);
    }

    /**
     * Check whether this member is overridden or not.
     * 
     * @return Result.
     */
    public final boolean isOverridden() {
        return e.getAnnotation(Override.class) != null;
    }

    /**
     * Build modifier element.
     * 
     * @return
     */
    public final XML createModifier() {
        Set<Modifier> visibility = new HashSet();
        Set<Modifier> nonvisibility = new HashSet();
        boolean isPackagePrivate = true;

        for (Modifier modifier : modifiers) {
            switch (modifier) {
            case ABSTRACT:
            case FINAL:
            case VOLATILE:
            case SYNCHRONIZED:
            case TRANSIENT:
            case DEFAULT:
                nonvisibility.add(modifier);
                break;

            case PUBLIC:
            case PROTECTED:
            case PRIVATE:
                isPackagePrivate = false;
                // fall-throgh

            default:
                visibility.add(modifier);
                break;
            }
        }

        XML xml = I.xml("<i/>");
        if (isPackagePrivate) xml.addClass("PACKAGEPRIVATE");
        if (isOverridden()) xml.addClass("OVERRIDE");
        for (Modifier modifier : visibility) {
            xml.addClass(modifier.name());
        }

        if (!nonvisibility.isEmpty()) {
            xml = I.xml("<i/>").append(xml);
            for (Modifier modifier : nonvisibility) {
                xml.addClass(modifier.name());
            }
        }
        return xml;
    }

    /**
     * Build name element.
     * 
     * @return
     */
    public final XML createName() {
        return I.xml("code").child("a").attr("href", "#" + id()).text(name).parent();
    }

    /**
     * Compute the member identifier.
     * 
     * @return
     */
    public abstract String id();
}