package dev.booky.betterview.nms;
// Created by booky10 in BetterView (16:21 03.06.2025)

import dev.booky.betterview.common.util.ServicesUtil;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface PaperNmsInterface {

    PaperNmsInterface SERVICE = ServicesUtil.loadService(PaperNmsInterface.class);

    long getNanosPerServerTick();
}
