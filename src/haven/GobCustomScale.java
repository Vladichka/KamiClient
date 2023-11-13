package haven;

import haven.render.Location;
import haven.render.Pipe;

public class GobCustomScale implements Gob.SetupMod {
    
    private Pipe.Op op = null;
    private int scale = 100;
    
    public void update(Gob gob) {
	String res = gob.resid();
	if(res == null) {
	    op = null;
	    return;
	}
	if(res.equals("gfx/terobjs/cupboard") && !CFG.FLOATING_DECALS.get()) {
	    update(CFG.CUPBOARD_HEIGHT.get());
	} else if(Utils.WALLS_TO_RESIZE.contains(res)) {
	    update(CFG.PALISADE_HEIGHT.get());
	}
    }
    
    private void update(int percent) {
	if(percent != scale) {
	    scale = percent;
	    op = makeScale(percent);
	}
    }
    
    private Pipe.Op makeScale(int percent) {
	if(percent == 100) {
	    return null;
	}
	
	float scale = percent / 100f;
	return new Location(new Matrix4f(
	    1, 0, 0, 0,
	    0, 1, 0, 0,
	    0, 0, scale, 0,
	    0, 0, 0, 1));
    }
    
    @Override
    public Pipe.Op gobstate() {
	return op;
    }
}