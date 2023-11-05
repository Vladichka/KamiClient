package haven;

import haven.render.Homo3D;
import haven.render.Pipe;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class GobOpeningOverlay extends GobInfo {
    private Fightview.Relation relation;
    protected Coord3f stance;
    protected Coord3f openings;
    private Coord buffSize = UI.scale(new Coord(26, 26));
    private Coord buffSizeCenter = UI.scale(new Coord(13,13));
    
    private static final Map<String, Color> OPENINGS = new HashMap<String, Color>(4) {{
	put("paginae/atk/offbalance", new Color(81, 165, 56));
	put("paginae/atk/reeling", new Color(210, 210, 64));
	put("paginae/atk/dizzy", new Color(39, 82, 191));
	put("paginae/atk/cornered", new Color(192, 28, 28));
    }};
    
    public GobOpeningOverlay(Gob owner) {
	super(owner);
	
	this.stance = new Coord3f(0.0F, 0.0F, 20.0F);
	this.openings = new Coord3f(0.0F, 0.0F, 20.0F);
	
	this.oip = new Text.UText<Integer>(opIp) {
	    public String text(Integer v) {
		return v.toString();
	    }
	    
	    public Integer value() {
		if(relation == null)
		    return 0;
		return relation.oip;
	    }
	};
	
	
	this.mip = new Text.UText<Integer>(myIp) {
	    public String text(Integer v) {
		return v.toString();
	    }
	    
	    public Integer value() {
		if(relation == null)
		    return 0;
		return relation.ip;
	    }
	};
    }
    
    protected boolean enabled() {
	return CFG.DRAW_OPENINGS_OVER_GOBS.get();
    }
    
    private static final Text.Furnace opIp = (Text.Furnace) new PUtils.BlurFurn((Text.Furnace) (new Text.Foundry(Text.mono, 18, new Color(255, 50, 100))).aa(false), 2, 2, new Color(0, 0, 0));
    private static final Text.Furnace myIp = (Text.Furnace) new PUtils.BlurFurn((Text.Furnace) (new Text.Foundry(Text.mono, 18, new Color(100, 255, 100))).aa(false), 2, 2, new Color(0, 0, 0));
    private final Text.UText<?> oip;
    private final Text.UText<?> mip;
    
    public void draw(GOut g, Pipe state) {
	if(relation != null && (CFG.DRAW_OPENINGS_OVER_GOBS.get())) {
	    Coord3f coordsOpenings = Homo3D.obj2view2(openings, state, Area.sized(g.sz()));
	    Coord3f coordsStance = Homo3D.obj2view2(stance, state, Area.sized(g.sz()));
	    if(coordsOpenings == null || coordsStance == null)
		return;
	    
	    Coord sc = coordsOpenings.round2().sub(0,UI.scale(40));
	    Coord sc3 = coordsStance.round2().sub(0,UI.scale(80));
	    
	    int count = 0;
	    for (Buff buff : this.relation.buffs.children(Buff.class)) {
		if(buff.isOpening()) {
		    count++;
		}
	    }
	    
	    sc.x -= UI.scale(42);

//	    g.chcolor(new Color(0,0,0,180));
//	    g.frect(sc.sub(0,mip.get().tex().sz().y), mip.get().tex().sz());
	    g.chcolor(Color.white);
	    g.aimage(this.mip.get().tex(), sc, 0.0D, 1.0D);

//	    g.chcolor(new Color(0,0,0,180));
//	    g.frect(sc.sub((UI.scale(80) - oip.get().tex().sz().x)*-1,oip.get().tex().sz().y), oip.get().tex().sz());
	    g.chcolor(Color.white);
	    g.aimage(this.oip.get().tex(), new Coord(sc.x + UI.scale(84) - oip.get().tex().sz().x, sc.y), 0.0D, 1.0D);
	    
	    sc.y += UI.scale(10);
	    sc.x += UI.scale(42);
	    
	    sc.x -= count * buffSize.x / 2;
	    if (count > 0) {
		g.chcolor(new Color(0,0,0,180));
		g.frect(sc.sub(UI.scale(new Coord(1, 1))), new Coord((buffSize.x * count) + UI.scale(count + 1), buffSize.y + UI.scale(2)));
	    }
	    
	    for (Buff buff : this.relation.buffs.children(Buff.class)) {
		if(buff.isOpening()) {
		    //buff.draw(g.reclip(sc, buff.sz));
		    g.chcolor(OPENINGS.get(buff.res.get().name));
		    g.frect(sc, buffSize);
		    g.chcolor(Color.WHITE);
		    Tex number = openingValue(buff.ameter);
		    g.aimage(number, sc.add(buffSizeCenter), 0.5, 0.5);
		    sc.x += buffSize.x + UI.scale(1);
		    continue;
		}

		try {
		    Tex stanceImg = buff.res.get().flayer(Resource.imgc).tex();
		    Coord stanceCoord = sc3.sub(new Coord(buffSizeCenter.x, 0)).add(0,buffSizeCenter.y);
		    g.chcolor(new Color(0,0,0,180));
		    g.frect(stanceCoord.sub(UI.scale(new Coord(1,1))), buffSize.add(UI.scale(new Coord(2,2))));
		    g.chcolor(255, 255, 255, 255);
		    g.image(stanceImg, stanceCoord, buffSize);
		} catch (Exception ex) {System.out.println("too dumb to programm");}
	    }
	}
    }
    public static final Text.Foundry nfnd = new Text.Foundry(Text.dfont.deriveFont(Font.BOLD), 12);
    
    private Tex openingValue(int value) {
	return new TexI(Utils.outline2(nfnd.render(Integer.toString(value), Color.WHITE).img, Color.BLACK));
    }
    
    public void ctick(double dt) {}
    
    
    protected Tex render() {
	return null;
    }
    
    public void destroy() {
	this.gob.clearOpenings();
    }
    
    public void update(Fightview.Relation rel) {
	this.relation = rel;
    }
    
}
