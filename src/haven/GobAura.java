package haven;

import haven.render.RenderTree;
import haven.sprites.AuraCircleSprite;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static haven.GobWarning.WarnMethod.*;
import static haven.GobWarning.WarnTarget.*;

public class GobAura extends GAttrib implements RenderTree.Node {
    private final AuraCircleSprite radius;
    
    public GobAura(Gob gob, AuraCircleSprite spr) {
	super(gob);
	if (spr == null)
	    radius = new AuraCircleSprite(gob, AuraCircleSprite.yellow);
	else
	    radius = spr;
    }
    
    @Override
    public void added(RenderTree.Slot slot) {
	super.added(slot);
	slot.add(radius);
    }
    
}
