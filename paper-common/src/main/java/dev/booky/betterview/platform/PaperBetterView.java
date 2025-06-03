package dev.booky.betterview.platform;
// Created by booky10 in BetterView (15:42 03.06.2025)

import dev.booky.betterview.common.hooks.BetterViewHook;
import dev.booky.betterview.nms.PaperNmsInterface;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PaperBetterView implements BetterViewHook {

    @Override
    public long getNanosPerServerTick() {
        return PaperNmsInterface.SERVICE.getNanosPerServerTick();
    }
}
