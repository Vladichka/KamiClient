package haven.opt;

import haven.*;
import me.ender.ui.CFGBox;
import static haven.OptWnd.*;


public interface KamiOptPanels {
	static void initMinimapPanel(OptWnd wnd, OptWnd.Panel panel) {
		int STEP = UI.scale(25);
		int START;
		int x, y;
		int my = 0, tx;

		Widget title = panel.add(new Label("Minimap / Map settings", LBL_FNT), 0, 0);
		START = title.sz.y + UI.scale(10);

		x = 0;
		y = START;
		//first row
		panel.add(new CFGBox("Enable PVP Map Mode", CFG.PVP_MAP, "Simplyfies the Map towards PVP."), x, y);

		y += STEP;
		panel.add(new CFGBox("Remove Biome Border from Minimap", CFG.REMOVE_BIOME_BORDER_FROM_MINIMAP), x, y);

		//	y += STEP;
		//	panel.add(new CFGBox("Show names of party members", CFG.SHOW_PARTY_NAMES), x, y);
		//
		//	y += STEP;
		//	panel.add(new CFGBox("Show names of kinned players", CFG.SHOW_PLAYER_NAME), x, y);
		//
		//	y += STEP;
		//	panel.add(new CFGBox("Show names of red players", CFG.SHOW_RED_NAME), x, y);

		//second row
		my = Math.max(my, y);
		x += UI.scale(265);
		y = START;
		my = Math.max(my, y);

		panel.add(wnd.new PButton(UI.scale(200), "Back", 27, wnd.main), new Coord(0, my + UI.scale(35)));
		panel.pack();
		title.c.x = (panel.sz.x - title.sz.x) / 2;
	}


	static void initExperimentalPanel(OptWnd wnd, OptWnd.Panel panel) {
		int STEP = UI.scale(25);
		int START;
		int x, y;
		int my = 0, tx;

		Widget title = panel.add(new Label("Experimental settings", LBL_FNT), 0, 0);
		START = title.sz.y + UI.scale(10);

		x = 0;
		y = START;
		//first row
		panel.add(new CFGBox("Disable certain remote UI calls", CFG.IGNORE_CERTAIN_REMOTE_UI, "RemoteUI's of the type 'ui/rinit:3' are ignored if the first parameter matches the character name. Prevents the display of Realm invites. Might prevent other things too."), x, y);

		//second row
		my = Math.max(my, y);
		x += UI.scale(265);
		y = START;


		my = Math.max(my, y);

		panel.add(wnd.new PButton(UI.scale(200), "Back", 27, wnd.main), new Coord(0, my + UI.scale(35)));
		panel.pack();
		title.c.x = (panel.sz.x - title.sz.x) / 2;
	}

}
