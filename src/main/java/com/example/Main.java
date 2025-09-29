package com.example;

import com.example.api.ElpriserAPI;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static java.lang.String.valueOf;

public class Main {
    public static void main(String[] args) {

        String zone = null;
        String datumStr = null;
        boolean sorted = false;
        String charging = null;

        for (int i = 0; i< args.length; i++) {
            switch (args[i]) {
                case "--zone":
                    zone = args[++i];
                    break;
                case "--date":
                    datumStr = args[++i];
                    break;
                case "--sorted":
                    sorted = true;
                    break;
                case "--charging":
                    charging = args[++i];
                    break;
                case "--help":
                    System.out.println("Användning: java -jar elpriser.jar [--zone <SE1|SE2|SE3|SE4>] [--date <YYYY-MM-DD>] [--sorted] [--charging <kW>]");
                    return;
                default:
                    System.out.println("Okänt argument: " + args[i]);
                    return;
            }
        }

        ElpriserAPI api = new ElpriserAPI();
        LocalDate idag = (datumStr != null) ? LocalDate.parse(datumStr) : LocalDate.now();
        List<ElpriserAPI.Elpris> dagensPriser = api.getPriser(
                idag, ElpriserAPI.Prisklass.valueOf(zone != null ? zone : args[0]));

        if (dagensPriser.isEmpty()) {
            System.out.println("Finns inga priser för idag, försök senare.");
        } else {
            System.out.println("\nDagens elpriser för " + ElpriserAPI.Prisklass.valueOf(zone != null ? zone : args[0]) + " (" + dagensPriser.size() + " st värden):");

        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("sv", "SE"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        if (sorted) {
            dagensPriser.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh).reversed());
        }

        dagensPriser.forEach(pris -> {
            int timeStart = pris.timeStart().getHour();
            int timeEnd = timeStart + 1;
            String tidsIntervall = String.format("%02d-%02d", timeStart, timeEnd);
            String prisFormatted = formatter.format(pris.sekPerKWh() * 100);

            System.out.println("\"" + tidsIntervall + " " + prisFormatted + " öre\"");
            //if(dagensPriser.size() > 3) System.out.println("...");
        });

        /*System.out.println("\n--- Anropar igen för samma dag ---");
        api.getPriser(idag, ElpriserAPI.Prisklass.SE3);

        System.out.println("\n--- Hämtar priser för 2025-09-25 i SE4 ---");
        List<ElpriserAPI.Elpris> framtidaPriser = api.getPriser("2025-09-25", ElpriserAPI.Prisklass.SE4);
        if (framtidaPriser.isEmpty()) {
            System.out.println("Inga priser hittades för 2025-09-25");
        }*/
        }
    }
}
