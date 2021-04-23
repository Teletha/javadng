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

import stoneforge.HTML;
import stoneforge.javadoc.analyze.ClassInfo;
import stylist.Style;
import stylist.StyleDSL;
import stylist.property.Background.BackgroundImage;
import stylist.value.Color;
import stylist.value.Numeric;

/**
 * 
 */
public class MainPage extends HTML {

    protected final ClassInfo info;

    protected final JavadocModel model;

    protected final boolean api;

    /**
     * @param info
     */
    public MainPage(JavadocModel model, ClassInfo info, boolean api) {
        this.info = info;
        this.model = model;
        this.api = api;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void declare() {
        $("html", () -> {
            $("head", () -> {
                $("meta", attr("charset", "UTF-8"));
                $("title", text(model.product() + " API"));
                script("https://unpkg.com/vue/dist/vue.js");
                script("https://unpkg.com/vue-router/dist/vue-router.js");
                stylesheet("https://cdn.jsdelivr.net/npm/pretty-checkbox@3.0/dist/pretty-checkbox.min.css");
                script("https://cdn.jsdelivr.net/npm/pretty-checkbox-vue@1.1/dist/pretty-checkbox-vue.min.js");
                stylesheet("https://unpkg.com/vue-select@3.4.0/dist/vue-select.css");
                script("https://unpkg.com/vue-select@3.4.0");
                stylesheet("https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@10.7.1/build/styles/default.min.css");
                script("https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@10.7.1/build/highlight.min.js");
                stylesheet("/main.css");
            });
            $("body", Styles.workbench, () -> {
                // =============================
                // Top Navigation
                // =============================
                $("header", Styles.HeaderArea, () -> {
                    $("h1", Styles.HeaderTitle, code(model.product()));
                    $("nav", Styles.HeaderNav, () -> {
                        $("a", text("Document"));
                        $("a", text("API"));
                        $("a", text("Blog"));
                        $("a", text("Community"));
                    });
                });

                $("main", Styles.MainArea, () -> {
                    // =============================
                    // Left Side Navigation
                    // =============================
                    $("nav", Styles.TypeNavigation, () -> {
                        $("div");
                    });

                    // =============================
                    // Main Contents
                    // =============================
                    $("article", Styles.Contents, () -> {
                        $("router-view");
                        if (info != null) {
                            $(new ContentsView(model, info));
                        }
                    });

                    // =============================
                    // Right Side Navigation
                    // =============================
                    $("aside", Styles.SubNavigation, () -> {
                        $("div", Styles.SubNavigationStickyBlock, () -> {
                            if (info != null) {
                                $(new SubNavigationView(info));
                            }
                        });
                    });
                });

                script("root.js", model.data);
                script("main.js");
            });
        });
    }

    /**
     * Style definition.
     */
    private interface Styles extends StyleDSL, BaseStyle {

        Numeric LeftNavigationWidth = Numeric.of(17, vw);

        Style workbench = () -> {
            background.color(Color.rgb(235, 246, 247)).image(BackgroundImage.drawSlash(Color.rgb(220, 222, 225, 0.7), 3)).repeat();
            font.size(FontSize).family(fonts.base).color(palette.font);
            line.height(LineHeight);
        };

        Style HeaderArea = () -> {
            background.color(Color.Inherit).image(BackgroundImage.inherit()).repeat();
            position.sticky().top(0, rem);
            display.width(MaxWidth).height(HeaderHeight).zIndex(10).flex().alignItems.lastBaseline();
            margin.auto();
            border.bottom.color(palette.primary).width(1, px).solid();
        };

        Style HeaderTitle = () -> {
            font.size(2.5, rem).family(fonts.title).weight.normal().color(palette.primary);
        };

        Style HeaderNav = () -> {
            margin.left(3, rem);

            $.child(() -> {
                font.size(1.3, rem);
                display.inlineBlock();
                padding.horizontal(1, rem);
                position.relative();

                $.hover(() -> {
                    text.decoration.none();

                    $.after(() -> {
                        content.text("");
                        display.block().width(0, px).height(0, px);
                        border.width(8, px).solid().color(Color.Transparent);
                        margin.left(-4, px);
                        border.top.width(0, px);
                        border.bottom.width(6, px);
                        border.bottom.color(palette.primary);
                        position.absolute().top(100, percent).left(50, percent);
                    });
                });
            });
        };

        Style MainArea = () -> {
            display.width(MaxWidth).flex().direction.row();
            margin.auto();
        };

        Style TypeNavigation = () -> {
            flexItem.basis(LeftNavigationWidth).shrink(0);

            $.child(() -> {
                position.sticky().top(HeaderHeight);
                padding.top(BaseStyle.BlockVerticalGap);

                $.child(() -> {
                    margin.bottom(BaseStyle.BlockVerticalGap);
                });
            });

            $.select(".el-select", () -> {
                display.width(100, percent);
            });

            $.select(".el-checkbox", () -> {
                display.block();
            });
        };

        Style Contents = () -> {
            flexItem.grow(1);
            margin.horizontal(3, rem);
        };

        Style SubNavigation = () -> {
            flexItem.basis(RightNavigationWidth).shrink(0);
        };

        Style SubNavigationStickyBlock = () -> {
            position.sticky().top(HeaderHeight);
            display.block().height(Numeric.of(80, vh).subtract(HeaderHeight)).maxWidth(RightNavigationWidth);
            overflow.auto().scrollbar.thin();
            text.whiteSpace.nowrap();

            $.hover(() -> {
                overflow.y.auto();
            });

            $.child().child(() -> {
                padding.vertical(0.15, em);
            });
        };
    }
}