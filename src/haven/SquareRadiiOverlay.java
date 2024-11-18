package haven;

import java.util.function.Function;

public class SquareRadiiOverlay {
    private Area area;
    private final MCache map;
    private final MCache.OverlayInfo info;
    private MCache.Overlay ol;
    
    private final Gob gob;
    private final float radius;
    private final Function<Coord, Boolean> mask = this::mask;
    
    public SquareRadiiOverlay(Gob gob, float radius, MCache.OverlayInfo info) {
	this.map = gob.glob.map;
	this.info = info;
	
	this.gob = gob;
	this.radius = radius;
	
	update();
    }
    
    private Boolean mask(Coord c) {
	return c.mul(MCache.tilesz).add(MCache.tilesz.div(2, 2)).dist(gob.rc) <= radius;
    }
    
    public void add() {
	if(ol != null) {return;}
	ol = map.new Overlay(area, info);
	ol.mask(mask);
    }
    
    public void rem() {
	if(ol == null) {return;}
	ol.destroy();
	ol = null;
    }
    
    public void update() {
	int k = (int) (radius / MCache.tilesz.x) + 1;//add 1 tile border
	Coord c = gob.rc.floor(MCache.tilesz);
	area = Area.sized(c.sub(k, k), Coord.of(2 * k + 1));
	
	if(ol != null) {ol.update(area);}
    }
}
