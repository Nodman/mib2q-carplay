package com.luka.carplay.rgd;

import com.luka.carplay.framework.Log;
import de.audi.app.combi.bap.app.navi.AppConnectorNavi;
import de.audi.atip.interapp.combi.bap.navi.data.CombiBAPNaviLaneGuidanceData;
import de.audi.atip.interapp.combi.bap.navi.data.CombiBAPNaviManeuverDescriptor;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

/** Native VC arrow mode (issue #3): FOLLOW_STREET beyond the prepare zone, the real
 * maneuver inside it, no renderer dependency, KDK suppression released on shutdown.
 * The default 3D-arrow mode uses the same FOLLOW_STREET/prepare-zone rule on FctID 23. */
public final class NativeArrowsTest {
    public static final class HUD extends AppConnectorNavi {
        int main = -1, state = -1, descriptors;
        HUD() { super(null); } // bypass the native framework constructor
        public void updateManeuverDescriptor(CombiBAPNaviManeuverDescriptor[] d) { main = d[0].mainElement; descriptors++; }
        public void updateManeuverState(int s) { state = s; }
        public void updateDistanceToNextManeuver(int a, int b, boolean c, int d) { }
        public void updateExitView(int a, int b) { }
        public void updateLaneGuidance(boolean a, CombiBAPNaviLaneGuidanceData[] b) { }
        public void updateCurrentPositionInfo(String a) { }
        public void updateTurnToInfo(String a, String b) { }
    }
    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
    static void set(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value);
    }
    static RouteGuidance.State route(int type, int stepM) throws Exception {
        RouteGuidance rg = new RouteGuidance();
        Field sf = RouteGuidance.class.getDeclaredField("state"); sf.setAccessible(true);
        RouteGuidance.State s = (RouteGuidance.State) sf.get(rg);
        s.routeState = 1; s.maneuverCount = 1; s.maneuverOrder = new int[]{0};
        s.mType[0] = type; s.mDistance[0] = stepM; s.mVer[0] = 1;
        return s;
    }
    static void move(BAPBridge bridge, RouteGuidance.State s, int distM, int dirty) {
        s.distManeuverM = distM; s.dirtyMask = dirty;
        check(bridge.update(s), "update not published at " + distM + " m");
    }
    static BAPBridge bridge(HUD hud, boolean nativeArrows) throws Exception {
        BAPBridge bridge = new BAPBridge();
        set(bridge, "initialized", Boolean.TRUE); set(bridge, "appConnectorNavi", hud);
        set(bridge, "routeTextPublished", Boolean.TRUE);
        set(bridge, "nativeArrows", Boolean.valueOf(nativeArrows));
        set(bridge, "bapSessionStarted", Boolean.TRUE);
        return bridge;
    }
    public static void main(String[] args) throws Exception {
        Log.setLevel(-1);
        Field uf = Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        Unsafe unsafe = (Unsafe) uf.get(null);
        int icon = RouteGuidance.State.DIRTY_MANEUVER_ICON | RouteGuidance.State.DIRTY_DIST_MAN;
        int dist = RouteGuidance.State.DIRTY_DIST_MAN;

        HUD hud = (HUD) unsafe.allocateInstance(HUD.class);
        BAPBridge nat = bridge(hud, true);
        check(nat.isNativeArrows() && nat.isPresentationReady(), "native mode waits for a renderer");
        RouteGuidance.State s = route(ManeuverMapper.MT_LEFT_TURN, 900);   // city: prepare 1500 m
        move(nat, s, 5000, icon);
        check(hud.main == ManeuverMapper.FOLLOW_STREET && hud.state == 1, "far turn not shown as follow road");
        int sent = hud.descriptors;
        move(nat, s, 3000, dist);
        check(hud.descriptors == sent, "distance-only delta outside prepare resent the descriptor");
        move(nat, s, 1400, dist);
        check(hud.main != ManeuverMapper.FOLLOW_STREET && hud.state == 2, "turn not revealed on entering prepare zone");
        sent = hud.descriptors;
        move(nat, s, 600, dist);
        check(hud.descriptors == sent, "turn resent inside prepare zone");
        move(nat, s, 0, icon);
        check(hud.main != ManeuverMapper.FOLLOW_STREET, "unknown distance fell back to follow road");

        RouteGuidance.State hw = route(ManeuverMapper.MT_LEFT_TURN, 9000); // highway: prepare 3000 m
        move(nat, hw, 2800, icon);
        check(hud.main != ManeuverMapper.FOLLOW_STREET, "highway prepare zone ignored");
        move(nat, hw, 3500, dist);
        check(hud.main == ManeuverMapper.FOLLOW_STREET && hud.state == 1, "leaving prepare zone kept the turn");

        RouteGuidance.State arrive = route(ManeuverMapper.MT_ARRIVE_AT_DESTINATION, 900);
        move(nat, arrive, 5000, icon);
        check(hud.main != ManeuverMapper.FOLLOW_STREET, "arrival replaced by follow road");

        HUD stock = (HUD) unsafe.allocateInstance(HUD.class);
        BAPBridge arrow = bridge(stock, false);
        check(!arrow.isPresentationReady(), "3D-arrow mode ready without a renderer");
        RouteGuidance.State d = route(ManeuverMapper.MT_LEFT_TURN, 900);
        move(arrow, d, 5000, icon);
        check(stock.main == ManeuverMapper.FOLLOW_STREET && stock.state == 1, "3D-arrow mode showed a far turn early");
        move(arrow, d, 1400, dist);
        check(stock.main != ManeuverMapper.FOLLOW_STREET && stock.state == 2, "3D-arrow mode hid the turn inside prepare");

        NativeArrows.setKdkSuppressed(true);
        nat.onShutdown();
        check(!NativeArrows.isKdkSuppressed(), "shutdown left the KDK suppressed");
        System.out.println("NativeArrowsTest: follow road beyond prepare (city/highway), turn inside, arrival kept, 3D mode follows too, no renderer gate, KDK released PASS");
    }
}
