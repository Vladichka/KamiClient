package me.ender;

import haven.CFG;
import haven.MCache;
import haven.Material;
import haven.render.BaseColor;
import haven.render.States;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;

public class CFGOverlayId implements MCache.OverlayInfo {
    Material mat = new Material(BaseColor.fromColorAndAlpha(CFG.COLOR_MINE_SUPPORT_OVERLAY.get(), 0.25f), States.maskdepth);
    Material omat = new Material(BaseColor.fromColorAndAlpha(CFG.COLOR_MINE_SUPPORT_OVERLAY.get(), 0.75f), States.maskdepth);
    private final Collection<String> tags;
    
    public CFGOverlayId(CFG<Color> cfg, String tag) {
	tags = Collections.singletonList(tag);
	cfg.observe(this::update);
    }
    
    @Override
    public Collection<String> tags() {return tags;}
    
    @Override
    public Material mat() {return (mat);}
    
    @Override
    public Material omat() {return omat;}
    
    private void update(CFG<Color> cfg) {
	mat = new Material(BaseColor.fromColorAndAlpha(cfg.get(), 0.25f), States.maskdepth);
	omat = new Material(BaseColor.fromColorAndAlpha(cfg.get(), 0.75f), States.maskdepth);
    }
}
