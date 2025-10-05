package com.example;

import com.example.api.ElpriserAPI;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Main {
    public static void main(String[] args) {

        String zone = null;
        String dateStr = null;
        boolean sorted = false;
        String charging = null;

        if (args.length == 0) {
            System.out.println("Run application with --zone SE3 --date 2024-06-01 --sorted --charging 4h " +
                    "\n or --help to see usage.");
            return;
        }

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone":
                    zone = args[++i];
                    break;
                case "--date":
                    dateStr = args[++i];
                    break;
                case "--sorted":
                    sorted = true;
                    break;
                case "--charging":
                    charging = args[++i];
                    break;
                case "--help":
                    System.out.println("Usage: " +
                            "\n--zone SE1, SE2, SE3 or SE4 " +
                            "\n--date YYYY-MM-DD " +
                            "\n--sorted " +
                            "\n--charging --2h, 4h or 8h " +
                            "\n Example: --zone SE3 --date 2024-06-01 --sorted --charging 4h" +
                            "\n--help");
                    return;
                default:
                    System.out.println("Unknown argument: " + args[i]);
                    System.out.println("Run application with --help to see usage.");
                    return;
            }
            System.out.println("Argument " + i + ": " + args[i]);
        }

        ElpriserAPI api = new ElpriserAPI();

        LocalDate idag;
        if (dateStr != null) {
            if (!dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                System.out.println("Invalid date format: --date, must be in format YYYY-MM-DD");
                return;
            }
            idag = LocalDate.parse(dateStr);
        } else {
            idag = LocalDate.now();
        }
        LocalDate imorgon = idag.plusDays(1);

        if (zone == null || !List.of("SE1", "SE2", "SE3", "SE4").contains(zone)) {
            System.out.println("Invalid zone: --zone, must be one of SE1, SE2, SE3, SE4");
            return;
        }

        List<ElpriserAPI.Elpris> dagensPriser = new ArrayList<>(api.getPriser(idag, ElpriserAPI.Prisklass.valueOf(zone)));
        dagensPriser.addAll(api.getPriser(imorgon, ElpriserAPI.Prisklass.valueOf(zone)));

        if (dagensPriser.isEmpty()) {
            System.out.println("No data or prices available for the given date and zone.");
            return;
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("sv", "SE"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        List<double[]> hourlyPrice = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            double sum = 0;
            int count = 0;
            long epoch = 0;

            for (ElpriserAPI.Elpris pris : dagensPriser) {
                if (pris.timeStart().getHour() == hour) {
                    sum += pris.sekPerKWh();
                    count++;
                    if (epoch == 0) epoch = pris.timeStart().toEpochSecond(); // sätt epoch till första förekomsten
                }
            }

            if (count > 0) {
                hourlyPrice.add(new double[]{hour, sum / count, epoch});
            }
        }

        if (sorted) {
            hourlyPrice.sort((a, b) -> {
                int cmp = Double.compare(b[1], a[1]); // först pris (fallande)
                if (cmp == 0) return Double.compare(a[2], b[2]);
                return cmp;
            });
        }

        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        int minHour = -1;
        int maxHour = -1;
        double totalSum = 0;

        for (double[] entry : hourlyPrice) {
            int hour = (int) entry[0];
            double avgPrice = entry[1];

            String averagePriceFormatted = formatter.format(avgPrice * 100);
            System.out.printf("%02d-%02d %s öre%n", hour, (hour + 1) % 24, averagePriceFormatted);

            totalSum += avgPrice;
            if (avgPrice < minPrice) {minPrice = avgPrice; minHour = hour;}
            if (avgPrice > maxPrice) {maxPrice = avgPrice; maxHour = hour;}
        }
        double dailyAvg = totalSum / hourlyPrice.size();

        System.out.println("Lägsta pris: " + formatter.format(minPrice * 100)
                + " öre kl " + String.format("%02d-%02d", minHour, (minHour + 1) % 24));
        System.out.println("Högsta pris: " + formatter.format(maxPrice * 100)
                + " öre kl " + String.format("%02d-%02d", maxHour, (maxHour + 1) % 24));
        System.out.println("Medelpris: " + formatter.format(dailyAvg * 100) + " öre");

        if (charging != null) {
            if (!List.of("2h", "4h", "8h").contains(charging)) {
                System.out.println("Invalid argument for --charging. Use one of: 2h, 4h, 8h");
                return;
            }
            int windowSize = Integer.parseInt(charging.replace("h", ""));
            double minAvg = Double.MAX_VALUE;
            int bestStartHour = -1;

            for (int i = 0; i <= dagensPriser.size() - windowSize; i++) {
                double sum = 0;
                for (int j = 0; j < windowSize; j++) {
                    sum += dagensPriser.get(i + j).sekPerKWh();
                }
                double avg = sum / windowSize;
                if (avg < minAvg) {
                    minAvg = avg;
                    bestStartHour = i;
                }
            }
            ElpriserAPI.Elpris startPris = dagensPriser.get(bestStartHour);
            int startHour = startPris.timeStart().getHour();
            String avgFormatted = formatter.format(minAvg * 100);

            System.out.println("\nPåbörja laddning kl " + String.format("%02d:00", startHour));
            System.out.println("Medelpris för fönster: " + avgFormatted + " öre" );
        }
    }
}
