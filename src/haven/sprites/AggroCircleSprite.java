package haven.sprites;

import haven.Gob;
import haven.OptWnd;
import haven.render.RenderTree;
import haven.sprites.baseSprite.ColoredCircleSprite;

import java.awt.*;


public class AggroCircleSprite extends ColoredCircleSprite {
    public static final int id = -4214129;
    public static Color col = new Color(255,0,0,100); // TODO: CFG entry for that maybe
    private boolean alive = true;

    public AggroCircleSprite(final Gob g) {
        super(g, col, 4.6f, 6.1f, 0.5f);
    }

    public void rem() {
        alive = false;
    }

    @Override
    public boolean tick(double ddt) {
        return !alive;
    }

    @Override
    public void added(RenderTree.Slot slot) {
        super.added(slot);
    }
}
