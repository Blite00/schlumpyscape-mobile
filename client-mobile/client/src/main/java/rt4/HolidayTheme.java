package rt4;

import java.time.LocalDate;

/**
 * Date-driven holiday theming for the title / login screen.
 * Everything here is computed from the local date so the theme starts and
 * ends on its own - nothing to switch on or off.
 */
public class HolidayTheme {

	public static final int NONE = 0;
	public static final int CHRISTMAS = 1;
	public static final int HALLOWEEN = 2;
	public static final int EASTER = 3;

	private static int cachedDay = -1;
	private static int cachedTheme = NONE;

	/** The holiday currently running, or NONE. Recomputed once a day. */
	public static int active() {
		// test hook: -Drt4.holiday=christmas forces a theme so the art can be previewed off-season
		String force = System.getProperty("rt4.holiday");
		if (force != null) {
			if (force.equalsIgnoreCase("christmas")) {
				return CHRISTMAS;
			}
			if (force.equalsIgnoreCase("halloween")) {
				return HALLOWEEN;
			}
			if (force.equalsIgnoreCase("easter")) {
				return EASTER;
			}
			if (force.equalsIgnoreCase("none")) {
				return NONE;
			}
		}
		LocalDate now = LocalDate.now();
		int doy = now.getDayOfYear();
		if (doy == cachedDay && cachedTheme != -1) {
			return cachedTheme;
		}
		cachedDay = doy;
		cachedTheme = compute(now);
		return cachedTheme;
	}

	private static int compute(LocalDate d) {
		int month = d.getMonthValue();
		int day = d.getDayOfMonth();
		// Christmas: 1 December - 4 January (lined up with WoW's Winter Veil)
		if (month == 12 || (month == 1 && day <= 4)) {
			return CHRISTMAS;
		}
		// Halloween: 17 October - 1 November (lined up with WoW's Hallow's End)
		if ((month == 10 && day >= 17) || (month == 11 && day == 1)) {
			return HALLOWEEN;
		}
		// Easter: 21 - 29 March every year (lined up with WoW's Noblegarden)
		if (month == 3 && day >= 21 && day <= 29) {
			return EASTER;
		}
		return NONE;
	}

	/** Day-of-year of Easter Sunday (Meeus/Jones/Butcher computus). */
	private static int easterSunday(int year) {
		int a = year % 19;
		int b = year / 100;
		int c = year % 100;
		int d = b / 4;
		int e = b % 4;
		int f = (b + 8) / 25;
		int g = (b - f + 1) / 3;
		int h = (19 * a + b - d - g + 15) % 30;
		int i = c / 4;
		int k = c % 4;
		int l = (32 + 2 * e + 2 * i - h - k) % 7;
		int m = (a + 11 * h + 22 * l) / 451;
		int month = (h + l - 7 * m + 114) / 31;
		int day = ((h + l - 7 * m + 114) % 31) + 1;
		return LocalDate.of(year, month, day).getDayOfYear();
	}

	public static String name() {
		switch (active()) {
			case CHRISTMAS:
				return "Christmas";
			case HALLOWEEN:
				return "Halloween";
			case EASTER:
				return "Easter";
			default:
				return null;
		}
	}

	/** Bundled login-background art for the active holiday, or the everyday art. */
	public static String backgroundResource() {
		switch (active()) {
			case CHRISTMAS:
				return "/holiday/titlebg_christmas.png";
			case HALLOWEEN:
				return "/holiday/titlebg_halloween.png";
			case EASTER:
				return "/holiday/titlebg_easter.png";
			default:
				return "/holiday/titlebg_default.png";
		}
	}

	/** Bundled login music for the active holiday, or null to leave the music alone. */
	public static String musicResource() {
		switch (active()) {
			case CHRISTMAS:
				return "/holiday/christmas.wav";
			case HALLOWEEN:
				return "/holiday/halloween.wav";
			case EASTER:
				return "/holiday/easter.wav";
			default:
				return null;
		}
	}

	/**
	 * Holiday login music, taken from the tracks the client already ships. The client
	 * picks the first name that actually exists in the music archive, so a wrong guess
	 * degrades to the normal login music instead of breaking it.
	 */
	public static String[] musicCandidates() {
		switch (active()) {
			case CHRISTMAS:
				return new String[]{"jungle bells", "Jungle Bells", "scape santa", "christmas", "harmony"};
			case HALLOWEEN:
				return new String[]{"halloween", "spooky", "harmony"};
			case EASTER:
				return new String[]{"easter", "harmony"};
			default:
				return null;
		}
	}

	/** Message drawn under the login box while a holiday is running. */
	public static String banner() {
		switch (active()) {
			case CHRISTMAS:
				return "Merry Christmas - the Christmas event is live!";
			case HALLOWEEN:
				return "Happy Halloween - the Halloween event is live!";
			case EASTER:
				return "Happy Easter - the Easter event is live!";
			default:
				return null;
		}
	}
}
