import com.nokia.mid.ui.multipointtouch.MultipointTouch;
import com.nokia.mid.ui.multipointtouch.MultipointTouchListener;

/**
 * Мультитач Nokia Asha (S40 Developer Platform 2.0): все пальцы приходят через слушатель Multipoint Touch API,
 * события холста тогда отключаются. Класс создаётся по имени из MC (Class.forName), только если API есть на телефоне.
 */
public final class Multitouch implements MultipointTouchListener {
    public Multitouch() {
        MultipointTouch.getInstance().addMultipointTouchListener(this);
        MC.instance.mptListener = true;
    }

    public void pointersChanged(int[] ids) {
        MC mc = MC.instance;
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i], st = MultipointTouch.getState(id), t;
            if (st == MultipointTouch.POINTER_PRESSED) t = 0;
            else if (st == MultipointTouch.POINTER_DRAGGED) t = 1;
            else if (st == MultipointTouch.POINTER_RELEASED) t = 2;
            else continue;
            mc.pushEvent(t, MultipointTouch.getX(id), MultipointTouch.getY(id), id);
        }
    }
}
