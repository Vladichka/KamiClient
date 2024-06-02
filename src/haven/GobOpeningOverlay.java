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
    protected Coord3f lastAction;
    private Coord buffSize = UI.scale(new Coord(26, 26));
    private Coord lastActionSize = UI.scale(new Coord(32, 32));
    private Coord lastActionSizeOutline = UI.scale(new Coord(32, 32)).add(6,6);
    private Coord lastActionSizeOutlineOffset = new Coord(3,3);
    private Coord buffSizeCenter = UI.scale(new Coord(13,13));
    private Indir<Resource> lastaction1;
    private Indir<Resource> lastaction2;
    
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
	this.lastAction = new Coord3f(0.0F, 0.0F, -1.0F);
 
	this.lastaction1 = null;
	this.lastaction2 = null;
	
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
	double now = Utils.rtime();
	if(relation != null && (CFG.DRAW_OPENINGS_OVER_GOBS.get())) {
	    Coord3f coordsOpenings = Homo3D.obj2view2(openings, state, Area.sized(g.sz()));
	    Coord3f coordsStance = Homo3D.obj2view2(stance, state, Area.sized(g.sz()));
	    Coord3f coordsLastAction = Homo3D.obj2view2(lastAction, state, Area.sized(g.sz()));
	    if(coordsOpenings == null || coordsStance == null || coordsLastAction == null)
		return;
	    
	    Coord sc = coordsOpenings.round2().sub(0,UI.scale(40));
	    Coord sc3 = coordsStance.round2().sub(0,UI.scale(80));
	    
	    Coord sc2 = coordsLastAction.round2().add(0, buffSize.y);
	    
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
		    Tex number = openingValue(buff.ameter());
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
	    
	    try {
	    	Indir<Resource> lastact = this.relation.lastact;
	    	if (lastact != this.lastaction2) {
		    this.lastaction2 = lastact;
	        }
	        double lastuse = this.relation.lastuse;
	     	if (lastact != null) {
		    Tex ut = ((Resource.Image)((Resource)lastact.get()).flayer(Resource.imgc)).tex();
		    Coord useul = new Coord(sc2.x - (lastActionSize).x / 2, sc2.y - (lastActionSize).y / 2);
		    
		    g.chcolor(new Color(0,0,0,180));
		    g.image(ut, useul.sub(2,2), lastActionSize.add(4,4));
	     
		    double sec = now - lastuse;
		    double cd = 1.0D; // TODO: calculate that somehow
		    if (sec < cd) {
			g.chcolor(new Color(255, 0, 0));
			int height = lastActionSizeOutline.mul(0, cd-sec).y;
			int offsetY = lastActionSizeOutline.mul(0, sec).y;
			g.frect(useul.sub(lastActionSizeOutlineOffset).addy(offsetY), new Coord(lastActionSizeOutline.x, height));
		    }
		    
		    g.chcolor(new Color(255,255,255));
		    g.image(ut, useul, lastActionSize);
	     	}
	    } catch (Loading loading) {}
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
    
    public void update(Fightview.Relation rel) {
	this.relation = rel;
    }
    
}
