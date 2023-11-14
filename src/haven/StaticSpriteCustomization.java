package haven;

import haven.render.Location;
import haven.render.RenderTree;
import java.util.Objects;

public class StaticSpriteCustomization {
    public static void added(StaticSprite sprite, RenderTree.Slot slot) {
	try {
	    if (CFG.RELOCATE_DECALS.get()
		&& sprite.res.name.equals("gfx/terobjs/items/parchment-decal")
		&& sprite.owner.getres().name.equals("gfx/terobjs/cupboard"))
	    {
		slot.cstate(Location.xlate(new Coord3f(-5,-5,17.5f)));
	    }
	} catch (Exception ignored) {}
    }
    
    public static <I, L extends Resource.IDLayer<I>> boolean needReturnNull(Resource res, Class<L> cl, I id) {
	//skip 'decal' bone offset for cupboards so decals would be positioned statically at (0,0,0) and not moving on the door
	if(CFG.RELOCATE_DECALS.get()
	    && cl == Skeleton.BoneOffset.class
	    && res.name.equals("gfx/terobjs/cupboard")
	    && Objects.equals(id, "decal")) {
	    return true;
	}
	
	return false;
    }
}
