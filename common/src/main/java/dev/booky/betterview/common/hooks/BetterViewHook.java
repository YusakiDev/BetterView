package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:08 03.06.2025)

import dev.booky.betterview.common.config.BvGlobalConfig;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface BetterViewHook {

    BvGlobalConfig getConfig();
}
