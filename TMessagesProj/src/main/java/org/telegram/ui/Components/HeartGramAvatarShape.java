package org.telegram.ui.Components;

import android.graphics.Path;

public final class HeartGramAvatarShape {
    public static final int ROUND = 0;
    public static final int SQUIRCLE = 1;
    public static final int STAR = 2;
    public static final int FLOWER = 3;
    public static final int HEXAGON = 4;
    public static final int COOKIE = 5, CLOVER = 6, HEART = 7, DIAMOND = 8, PENTAGON = 9,
            OCTAGON = 10, CLOUD = 11, DROP = 12, CAT = 13, SHIELD = 14, LEAF = 15,
            CROSS = 16, BUTTERFLY = 17, ROSETTE = 18, SPARK = 19, ARCH = 20;

    private HeartGramAvatarShape() {
    }

    public static void build(Path path, int shape) {
        path.reset();
        if (shape == SQUIRCLE) {
            path.addRoundRect(0, 0, 1, 1, .25f, .25f, Path.Direction.CW);
            return;
        }
        if (shape >= COOKIE && shape <= ARCH) {
            buildExtra(path, shape);
            return;
        }
        if (shape != STAR && shape != FLOWER && shape != HEXAGON) {
            path.addCircle(.5f, .5f, .5f, Path.Direction.CW);
            return;
        }
        final int count = shape == STAR ? 10 : shape == HEXAGON ? 6 : 48;
        if (shape == STAR || shape == HEXAGON) {
            polygon(path, count, shape == STAR ? .30 : .50, -Math.PI / 2);
            return;
        }
        lobes(path, 6, .43, .07, 0);
    }

    private static void polygon(Path path, int count, double innerRadius, double start) {
            for (int point = 0; point < count; point++) {
                double angle = start + point * 2 * Math.PI / count;
                double radius = point % 2 == 1 ? innerRadius : .50;
                double adjacentRadius = point % 2 == 1 ? .50 : innerRadius;
                double step = 2 * Math.PI / count;
                float x = coordinate(Math.cos(angle), radius);
                float y = coordinate(Math.sin(angle), radius);
                float startX = .85f * x + .15f * coordinate(Math.cos(angle - step), adjacentRadius);
                float startY = .85f * y + .15f * coordinate(Math.sin(angle - step), adjacentRadius);
                float endX = .85f * x + .15f * coordinate(Math.cos(angle + step), adjacentRadius);
                float endY = .85f * y + .15f * coordinate(Math.sin(angle + step), adjacentRadius);
                if (point == 0) {
                    path.moveTo(startX, startY);
                } else {
                    path.lineTo(startX, startY);
                }
                path.quadTo(x, y, endX, endY);
            }
            path.close();
    }

    private static void lobes(Path path, int petals, double mean, double depth, double phase) {
        for (int point = 0; point < 64; point++) {
            final double a = -Math.PI / 2 + point * 2 * Math.PI / 64;
            final double radius = mean + depth * Math.cos(petals * (a + phase));
            final float x = coordinate(Math.cos(a), radius);
            final float y = coordinate(Math.sin(a), radius);
            if (point == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
    }

    private static void buildExtra(Path path, int shape) {
        switch (shape) {
            case COOKIE:
                lobes(path, 10, .46, .027, .14);
                Path bite = new Path();
                bite.addCircle(.89f, .09f, .24f, Path.Direction.CW);
                bite.addCircle(.99f, .25f, .19f, Path.Direction.CW);
                path.op(bite, Path.Op.DIFFERENCE);
                return;
            case CLOVER:
                lobes(path, 4, .395, .101, Math.PI / 4);
                return;
            case DIAMOND:
                polygon(path, 4, .5, -Math.PI / 2);
                return;
            case PENTAGON:
                polygon(path, 5, .5, -Math.PI / 2);
                return;
            case OCTAGON:
                polygon(path, 8, .5, -Math.PI * 3 / 8);
                return;
            case ROSETTE:
                lobes(path, 12, .465, .032, .1);
                return;
            case SPARK:
                lobes(path, 4, .36, .137, 0);
                return;
            case HEART:
                path.moveTo(.5f, .96f);
                path.cubicTo(.37f, .82f, .035f, .59f, .026f, .32f);
                path.cubicTo(.02f, .052f, .35f, .012f, .5f, .222f);
                path.cubicTo(.65f, .012f, .98f, .052f, .974f, .32f);
                path.cubicTo(.965f, .59f, .63f, .82f, .5f, .96f);
                break;
            case CLOUD:
                path.moveTo(.23f, .85f);
                path.cubicTo(-.04f, .85f, -.06f, .48f, .17f, .43f);
                path.cubicTo(.16f, .24f, .36f, .17f, .44f, .25f);
                path.cubicTo(.59f, .02f, .84f, .15f, .84f, .41f);
                path.cubicTo(1.07f, .43f, 1.05f, .85f, .78f, .85f);
                break;
            case DROP:
                path.moveTo(.5f, .035f);
                path.cubicTo(.61f, .22f, .94f, .51f, .94f, .66f);
                path.cubicTo(.94f, 1.07f, .06f, 1.07f, .06f, .66f);
                path.cubicTo(.06f, .51f, .39f, .22f, .5f, .035f);
                break;
            case CAT:
                path.moveTo(.08f, .52f);
                path.quadTo(.045f, .08f, .13f, .055f);
                path.quadTo(.2f, .047f, .35f, .28f);
                path.quadTo(.5f, .23f, .65f, .28f);
                path.quadTo(.8f, .047f, .87f, .055f);
                path.quadTo(.96f, .08f, .92f, .52f);
                path.cubicTo(1.09f, .99f, .655f, .975f, .5f, .975f);
                path.cubicTo(.345f, .975f, -.09f, .99f, .08f, .52f);
                break;
            case SHIELD:
                path.moveTo(.5f, .045f);
                path.quadTo(.73f, .13f, .9f, .16f);
                path.quadTo(.95f, .18f, .94f, .24f);
                path.cubicTo(.93f, .61f, .79f, .86f, .5f, .964f);
                path.cubicTo(.21f, .86f, .07f, .61f, .06f, .24f);
                path.quadTo(.05f, .18f, .1f, .16f);
                path.quadTo(.27f, .13f, .5f, .045f);
                break;
            case LEAF:
                path.moveTo(.94f, .075f);
                path.cubicTo(.44f, -.02f, .05f, .31f, .065f, .65f);
                path.cubicTo(.07f, .88f, .22f, 1.01f, .45f, .955f);
                path.cubicTo(.83f, .87f, .98f, .50f, .94f, .075f);
                break;
            case CROSS:
                path.moveTo(.32f, .12f);
                path.quadTo(.32f, .045f, .402f, .045f);
                path.lineTo(.598f, .045f);
                path.quadTo(.68f, .045f, .68f, .12f);
                path.lineTo(.68f, .32f);
                path.lineTo(.88f, .32f);
                path.quadTo(.955f, .32f, .955f, .402f);
                path.lineTo(.955f, .598f);
                path.quadTo(.955f, .68f, .88f, .68f);
                path.lineTo(.68f, .68f);
                path.lineTo(.68f, .88f);
                path.quadTo(.68f, .955f, .598f, .955f);
                path.lineTo(.402f, .955f);
                path.quadTo(.32f, .955f, .32f, .88f);
                path.lineTo(.32f, .68f);
                path.lineTo(.12f, .68f);
                path.quadTo(.045f, .68f, .045f, .598f);
                path.lineTo(.045f, .402f);
                path.quadTo(.045f, .32f, .12f, .32f);
                path.lineTo(.32f, .32f);
                break;
            case BUTTERFLY:
                path.moveTo(.5f, .4f);
                path.cubicTo(.12f, .015f, -.13f, .16f, .13f, .54f);
                path.cubicTo(-.015f, .82f, .22f, 1.06f, .5f, .72f);
                path.cubicTo(.78f, 1.06f, 1.015f, .82f, .87f, .54f);
                path.cubicTo(1.13f, .16f, .88f, .015f, .5f, .4f);
                break;
            case ARCH:
                path.moveTo(.065f, .88f);
                path.lineTo(.065f, .465f);
                path.cubicTo(.065f, -.035f, .935f, -.035f, .935f, .465f);
                path.lineTo(.935f, .88f);
                path.quadTo(.935f, .96f, .85f, .96f);
                path.lineTo(.15f, .96f);
                path.quadTo(.065f, .96f, .065f, .88f);
                break;
        }
        path.close();
    }

    private static float coordinate(double direction, double radius) {
        return .5f + (float) (direction * radius);
    }
}
