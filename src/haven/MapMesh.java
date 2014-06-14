/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import static haven.MCache.tilesz;
import java.util.*;
import javax.media.opengl.*;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import haven.Surface.Vertex;
import haven.Surface.MeshVertex;

public class MapMesh implements Rendered, Disposable {
    public final Coord ul, sz;
    public final MCache map;
    private final long rnd;
    private Map<Tex, GLState[]> texmap = new HashMap<Tex, GLState[]>();
    private Map<DataID, Object> data = new LinkedHashMap<DataID, Object>();
    private List<Rendered> extras = new ArrayList<Rendered>();
    private FastMesh[] flats;
    private List<Disposable> dparts = new ArrayList<Disposable>();

    public interface DataID<T> {
	public T make(MapMesh m);
    }

    public static <T> DataID<T> makeid(Class<T> cl) {
	try {
	    final java.lang.reflect.Constructor<T> cons = cl.getConstructor(MapMesh.class);
	    return(new DataID<T>() {
		    public T make(MapMesh m) {
			return(Utils.construct(cons, m));
		    }
		});
	} catch(NoSuchMethodException e) {}
	try {
	    final java.lang.reflect.Constructor<T> cons = cl.getConstructor();
	    return(new DataID<T>() {
		    public T make(MapMesh m) {
			return(Utils.construct(cons));
		    }
		});
	} catch(NoSuchMethodException e) {}
	throw(new Error("No proper data-ID constructor found"));
    }

    public static interface ConsHooks {
	public void sfin();
	public void calcnrm();
	public void postcalcnrm(Random rnd);
	public boolean clean();
    }

    public static class Hooks implements ConsHooks {
	public void sfin() {};
	public void calcnrm() {}
	public void postcalcnrm(Random rnd) {}
	public boolean clean() {return(false);}
    }

    @SuppressWarnings("unchecked")
    public <T> T data(DataID<T> id) {
	T ret = (T)data.get(id);
	if(ret == null)
	    data.put(id, ret = id.make(this));
	return(ret);
    }

    public static class Scan {
        public final Coord ul, sz, br;
        public final int l;

        public Scan(Coord ul, Coord sz) {
            this.ul = ul;
            this.sz = sz;
            this.br = sz.add(ul);
            this.l = sz.x * sz.y;
        }

        public int o(int x, int y) {
            return((x - ul.x) + ((y - ul.y) * sz.x));
        }

        public int o(Coord in) {
            return(o(in.x, in.y));
        }
    }

    public class MapSurface extends haven.Surface implements ConsHooks {
	public final Scan vs = new Scan(new Coord(-1, -1), sz.add(3, 3));
	public final Scan ts = new Scan(Coord.z, sz);
	public final Vertex[] surf = new Vertex[vs.l];
	public final boolean[] split = new boolean[ts.l];

	public MapSurface() {
	    for(int y = vs.ul.y; y < vs.br.y; y++) {
		for(int x = vs.ul.x; x < vs.br.x; x++) {
		    surf[vs.o(x, y)] = new Vertex(x * tilesz.x, y * -tilesz.y, map.getz(ul.add(x, y)));
		}
	    }
	    for(int y = ts.ul.y; y < ts.br.y; y++) {
		for(int x = ts.ul.x; x < ts.br.x; x++) {
		    split[ts.o(x, y)] = Math.abs(surf[vs.o(x, y)].z - surf[vs.o(x + 1, y + 1)].z) > Math.abs(surf[vs.o(x + 1, y)].z - surf[vs.o(x, y + 1)].z);
		}
	    }
	}

	public Vertex fortile(Coord c) {
	    return(surf[vs.o(c)]);
	}

	public Vertex[] fortilea(Coord c) {
	    return(new Vertex[] {
		    surf[vs.o(c.x, c.y)],
		    surf[vs.o(c.x, c.y + 1)],
		    surf[vs.o(c.x + 1, c.y + 1)],
		    surf[vs.o(c.x + 1, c.y)],
		});
	}

	public void sfin() {fin();}
	public void calcnrm() {}
	public void postcalcnrm(Random rnd) {}
	public boolean clean() {return(true);}
    }
    public static final DataID<MapSurface> gnd = makeid(MapSurface.class);

    public static class MLOrder extends Order<Rendered> {
	public final int z;

	public MLOrder(int z, int subz) {
	    this.z = (z << 8) + subz;
	}

	public MLOrder(int z) {
	    this(z, 0);
	}

	public int mainz() {
	    return(1000);
	}

	public boolean equals(Object x) {
	    return((x instanceof MLOrder) && (((MLOrder)x).z == this.z));
	}

	public int hashCode() {
	    return(z);
	}

	private final static RComparator<Rendered> cmp = new RComparator<Rendered>() {
	    public int compare(Rendered a, Rendered b, GLState.Buffer sa, GLState.Buffer sb) {
		return(((MLOrder)sa.get(order)).z - ((MLOrder)sb.get(order)).z);
	    }
	};

	public RComparator<Rendered> cmp() {return(cmp);}
    }

    private MapMesh(MCache map, Coord ul, Coord sz, Random rnd) {
	this.map = map;
	this.ul = ul;
	this.sz = sz;
	this.rnd = rnd.nextLong();
    }

    public Random rnd() {
	return(new Random(this.rnd));
    }

    public Random rnd(Coord c) {
	Random ret = rnd();
	ret.setSeed(ret.nextInt() + c.x);
	ret.setSeed(ret.nextInt() + c.y);
	return(ret);
    }

    public static Random grnd(Coord c) {
	Random ret = new Random(1192414289);
	ret.setSeed(ret.nextInt() + c.x);
	ret.setSeed(ret.nextInt() + c.y);
	return(ret);
    }
	
    private static void dotrans(MapMesh m, Random rnd, Coord lc, Coord gc) {
	Tiler ground = m.map.tiler(m.map.gettile(gc));
	int tr[][] = new int[3][3];
	int max = -1;
	for(int y = -1; y <= 1; y++) {
	    for(int x = -1; x <= 1; x++) {
		if((x == 0) && (y == 0))
		    continue;
		int tn = m.map.gettile(gc.add(x, y));
		tr[x + 1][y + 1] = tn;
		if(tn > max)
		    max = tn;
	    }
	}
	int bx[] = {0, 1, 2, 1};
	int by[] = {1, 0, 1, 2};
	int cx[] = {0, 2, 2, 0};
	int cy[] = {0, 0, 2, 2};
	for(int i = max; i >= 0; i--) {
	    int bm = 0, cm = 0;
	    for(int o = 0; o < 4; o++) {
		if(tr[bx[o]][by[o]] == i)
		    bm |= 1 << o;
	    }
	    for(int o = 0; o < 4; o++) {
		if((bm & ((1 << o) | (1 << ((o + 1) % 4)))) != 0)
		    continue;
		if(tr[cx[o]][cy[o]] == i)
		    cm |= 1 << o;
	    }
	    if((bm != 0) || (cm != 0)) {
		Tiler t = m.map.tiler(i);
		if(t == null)
		    continue;
		t.trans(m, rnd, ground, lc, gc, 255 - i, bm, cm);
	    }
	}
    }

    public static class Model extends MeshBuf implements ConsHooks {
	public final MapMesh m;
	public final GLState mat;

	public Model(MapMesh m, GLState mat) {
	    this.m = m;
	    this.mat = mat;
	}

	public void sfin() {}
	public void calcnrm() {}
	public boolean clean() {return(false);}

	public void postcalcnrm(Random rnd) {
	    FastMesh mesh = mkmesh();
	    m.extras.add(mat.apply(mesh));
	    m.dparts.add(mesh);
	}

	public static class MatKey implements DataID<Model> {
	    public final GLState mat;
	    private final int hash;

	    public MatKey(GLState mat) {
		this.mat = mat;
		this.hash = mat.hashCode() * 37;
	    }

	    public int hashCode() {
		return(hash);
	    }

	    public boolean equals(Object x) {
		return((x instanceof MatKey) && mat.equals(((MatKey)x).mat));
	    }

	    public Model make(MapMesh m) {
		return(new Model(m, mat));
	    }
	}

	public static Model get(MapMesh m, GLState mat) {
	    return(m.data(new MatKey(mat)));
	}
    }

    public static MapMesh build(MCache mc, Random rnd, Coord ul, Coord sz) {
	MapMesh m = new MapMesh(mc, ul, sz, rnd);
	Coord c = new Coord();
	rnd = m.rnd();
	
	for(c.y = 0; c.y < sz.y; c.y++) {
	    for(c.x = 0; c.x < sz.x; c.x++) {
		Coord gc = c.add(ul);
		long ns = rnd.nextLong();
		mc.tiler(mc.gettile(gc)).model(m, rnd, c, gc);
		rnd.setSeed(ns);
	    }
	}
	for(Object obj : m.data.values()) {
	    if(obj instanceof ConsHooks)
		((ConsHooks)obj).sfin();
	}
	for(c.y = 0; c.y < sz.y; c.y++) {
	    for(c.x = 0; c.x < sz.x; c.x++) {
		Coord gc = c.add(ul);
		long ns = rnd.nextLong();
		mc.tiler(mc.gettile(gc)).lay(m, rnd, c, gc);
		dotrans(m, rnd, c, gc);
		rnd.setSeed(ns);
	    }
	}
	for(Object obj : m.data.values()) {
	    if(obj instanceof ConsHooks)
		((ConsHooks)obj).calcnrm();
	}
	for(Object obj : m.data.values()) {
	    if(obj instanceof ConsHooks)
		((ConsHooks)obj).postcalcnrm(rnd);
	}
	
	m.consflat();
	
	m.clean();
	return(m);
    }

    private static States.DepthOffset gmoff = new States.DepthOffset(-1, -1);
    public static class GroundMod implements Rendered, Disposable {
	private static final Order gmorder = new Order.Default(1001);
	public final Material mat;
	public final Coord cc;
	public final FastMesh mesh;
	
	public GroundMod(MCache map, Tex tex, final Coord cc, final Coord ul, final Coord br) {
	    this.mat = new Material(tex);
	    this.cc = cc;
	    if(tex instanceof TexGL) {
		TexGL gt = (TexGL)tex;
		if(gt.wrapmode != GL2.GL_CLAMP_TO_BORDER) {
		    gt.wrapmode = GL2.GL_CLAMP_TO_BORDER;
		    gt.dispose();
		}
	    }
	    final MeshBuf buf = new MeshBuf();
	    final float cz = map.getcz(cc);
	    Tiler.MCons cons = new Tiler.MCons() {
		    final MeshBuf.Tex ta = buf.layer(MeshBuf.tex);
		    final Map<Vertex, MeshVertex> cv = new HashMap<Vertex, MeshVertex>();

		    public void faces(MapMesh m, Tiler.MPart d) {
			MeshVertex[] mv = new MeshVertex[d.v.length];
			for(int i = 0; i < d.v.length; i++) {
			    if((mv[i] = cv.get(d.v[i])) == null) {
				cv.put(d.v[i], mv[i] = new MeshVertex(buf, d.v[i]));
				mv[i].pos = mv[i].pos.add((m.ul.x * tilesz.x) - cc.x, cc.y - (m.ul.y * tilesz.y), -cz);
				Coord3f texc = new Coord3f((((m.ul.x + d.lc.x + d.tcx[i]) * tilesz.x) - ul.x) / (float)(br.x - ul.x),
							   (((m.ul.y + d.lc.y + d.tcy[i]) * tilesz.y) - ul.y) / (float)(br.y - ul.y),
							   0);
				ta.set(mv[i], texc);
			    }
			}
			for(int i = 0; i < d.f.length; i += 3)
			    buf.new Face(mv[d.f[i]], mv[d.f[i + 1]], mv[d.f[i + 2]]);
		    }
		};
	    Coord ult = ul.div(tilesz);
	    Coord brt = br.div(tilesz);
	    Coord t = new Coord();
	    for(t.y = ult.y; t.y <= brt.y; t.y++) {
		for(t.x = ult.x; t.x <= brt.x; t.x++) {
		    MapMesh cut = map.getcut(t.div(MCache.cutsz));
		    Tiler tile = map.tiler(map.gettile(t));
		    tile.lay(cut, t.sub(cut.ul), t, cons);
		}
	    }
	    mesh = buf.mkmesh();
	}

	@Deprecated
	public GroundMod(MCache map, DataID<?> surf, Tex tex, Coord cc, Coord ul, Coord br) {
	    this(map, tex, cc, ul, br);
	    if(surf != null)
		throw(new RuntimeException());
	}

	@Deprecated
	public GroundMod(MCache map, Class<?> surf, Tex tex, Coord cc, Coord ul, Coord br) {
	    this(map, (DataID<?>)null, tex, cc, ul, br);
	    if(surf != null)
		throw(new RuntimeException());
	}

	public void dispose() {
	    mesh.dispose();
	}

	public void draw(GOut g) {
	}
		
	public boolean setup(RenderList rl) {
	    rl.prepc(gmorder);
	    rl.prepc(mat);
	    rl.prepc(gmoff);
	    rl.add(mesh, null);
	    return(false);
	}
    }
    
    public static final Order olorder = new Order.Default(1002);
    public Rendered[] makeols() {
	final MeshBuf buf = new MeshBuf();
	final MapSurface ms = data(gnd);
	final MeshBuf.Vertex[] vl = new MeshBuf.Vertex[ms.vl.length];
	final haven.Surface.Normals sn = ms.data(haven.Surface.nrm);
	class Buf implements Tiler.MCons {
	    int[] fl = new int[16];
	    int fn = 0;

	    public void faces(MapMesh m, Tiler.MPart d) {
		for(Vertex v : d.v) {
		    if(vl[v.vi] == null)
			vl[v.vi] = buf.new Vertex(v, sn.get(v));
		}
		while(fn + d.f.length > fl.length)
		    fl = Utils.extend(fl, fl.length * 2);
		for(int fi : d.f)
		    fl[fn++] = d.v[fi].vi;
	    }
	}
	Coord t = new Coord();
	int[][] ol = new int[sz.x][sz.y];
	for(t.y = 0; t.y < sz.y; t.y++) {
	    for(t.x = 0; t.x < sz.x; t.x++) {
		ol[t.x][t.y] = map.getol(ul.add(t));
	    }
	}
	Buf[] bufs = new Buf[32];
	for(int i = 0; i < bufs.length; i++) {
	    bufs[i] = new Buf();
	    for(t.y = 0; t.y < sz.y; t.y++) {
		for(t.x = 0; t.x < sz.x; t.x++) {
		    if((ol[t.x][t.y] & (1 << i)) != 0) {
			Coord gc = t.add(ul);
			map.tiler(map.gettile(gc)).lay(this, t, gc, bufs[i]);
		    }
		}
	    }
	}
	Rendered[] ret = new Rendered[32];
	for(int i = 0; i < bufs.length; i++) {
	    if(bufs[i].fn > 0) {
		int[] fl = bufs[i].fl;
		int fn = bufs[i].fn;
		buf.clearfaces();
		for(int o = 0; o < fn; o += 3)
		    buf.new Face(vl[fl[o]], vl[fl[o + 1]], vl[fl[o + 2]]);
		final FastMesh mesh = buf.mkmesh();
		class OL implements Rendered, Disposable {
		    public void draw(GOut g) {
			mesh.draw(g);
		    }

		    public void dispose() {
			mesh.dispose();
		    }

		    public boolean setup(RenderList rl) {
			rl.prepo(olorder);
			return(true);
		    }
		}
		ret[i] = new OL();
	    }
	}
	return(ret);
    }

    private void clean() {
	texmap = null;
	int on = data.size();
	for(Iterator<Map.Entry<DataID, Object>> i = data.entrySet().iterator(); i.hasNext();) {
	    Object d = i.next().getValue();
	    if(!(d instanceof ConsHooks) || !((ConsHooks)d).clean())
		i.remove();
	}
    }
    
    public void draw(GOut g) {
    }
    
    private void consflat() {
	class Buf implements Tiler.MCons {
	    int vn = 0, in = 0, vl = sz.x * sz.y * 4;
	    float[] pos = new float[vl * 3];
	    float[] col1 = new float[vl * 4];
	    float[] col2 = new float[vl * 4];
	    short[] ind = new short[sz.x * sz.y * 6];

	    public void faces(MapMesh m, Tiler.MPart d) {
		if(vn + d.v.length > vl) {
		    vl *= 2;
		    pos = Utils.extend(pos, vl * 12);
		    col1 = Utils.extend(col1, vl * 16);
		    col2 = Utils.extend(col2, vl * 16);
		}
		float cx = (d.lc.x + 1) / 256.0f, cy = (d.lc.y + 1) / 256.0f;
		for(int i = 0; i < d.v.length; i++) {
		    int pb = (vn + i) * 3, cb = (vn + i) * 4;
		    pos[pb + 0] = d.v[i].x; pos[pb + 1] = d.v[i].y; pos[pb + 2] = d.v[i].z;
		    col1[cb + 0] = cx; col1[cb + 1] = cy; col1[cb + 2] = 0; col1[cb + 3] = 1;
		    col2[cb + 0] = d.tcx[i]; col2[cb + 1] = d.tcy[i]; col2[cb + 2] = 0; col2[cb + 3] = 1;
		}
		if(in + d.f.length > ind.length)
		    ind = Utils.extend(ind, ind.length * 2);
		for(int fi : d.f)
		    ind[in++] = (short)(vn + fi);
		vn += d.v.length;
	    }
	}
	Buf buf = new Buf();
	Coord c = new Coord();
	for(c.y = 0; c.y < sz.y; c.y++) {
	    for(c.x = 0; c.x < sz.x; c.x++) {
		Coord gc = c.add(ul);
		map.tiler(map.gettile(gc)).lay(this, c, gc, buf);
	    }
	}
	float[] pos = buf.pos, col1 = buf.col1, col2 = buf.col2;
	short[] ind = buf.ind;
	if(pos.length != buf.vn * 3) pos = Utils.extend(pos, buf.vn * 3);
	if(col1.length != buf.vn * 4) col1 = Utils.extend(col1, buf.vn * 4);
	if(col2.length != buf.vn * 4) col2 = Utils.extend(col2, buf.vn * 4);
	if(ind.length != buf.in) ind = Utils.extend(ind, buf.in);
	VertexBuf.VertexArray posa = new VertexBuf.VertexArray(FloatBuffer.wrap(pos));
	VertexBuf.ColorArray cola1 = new VertexBuf.ColorArray(FloatBuffer.wrap(col1));
	VertexBuf.ColorArray cola2 = new VertexBuf.ColorArray(FloatBuffer.wrap(col2));
	ShortBuffer indb = ShortBuffer.wrap(ind);
	flats = new FastMesh[] {
	    new FastMesh(new VertexBuf(posa), indb),
	    new FastMesh(new VertexBuf(posa, cola1), indb),
	    new FastMesh(new VertexBuf(posa, cola2), indb),
	};
    }

    public void drawflat(GOut g, int mode) {
	g.apply();
	flats[mode].draw(g);
    }
    
    public void dispose() {
	for(Disposable p : dparts)
	    p.dispose();
    }
    
    public boolean setup(RenderList rl) {
	for(Rendered e : extras)
	    rl.add(e, null);
	return(true);
    }
}
