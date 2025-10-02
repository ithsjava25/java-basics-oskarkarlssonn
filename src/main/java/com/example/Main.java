package com.example;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

import com.example.api.ElpriserAPI;

public class Main {
    public static void main(String[] args) {

        // --- START & HJÄLPTEXT ---
        // Visar hjälptext om inga argument ges eller om --help anges.
        if (args.length == 0) {
            System.out.println("usage");
            System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
            return;
        }
        boolean isTest = Arrays.asList(args).contains("--testmode");

        ElpriserAPI elpriserAPI = new ElpriserAPI();

        Scanner scanner = null; // Delay creation

        // --- ARGUMENTHANTERING OCH HJÄLP ---
        // Tolkar kommandoradsargument och visar hjälptext vid behov.
        String zone = null, dateStr = null, chargingStr = null;
        boolean sorted = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "--zone":
                    if (i + 1 < args.length) zone = args[++i].toUpperCase();
                    break;
                case "--date":
                    if (i + 1 < args.length) dateStr = args[++i];
                    break;
                case "--charging":
                    if (i + 1 < args.length) chargingStr = args[++i];
                    break;
                case "--sorted":
                    sorted = true;
                    break;
                case "--help":
                    skrivUtHjälp();
                    if (isTest) return;
            }
        }

        // --- VALIDERING AV ELPRISOMRÅDE ---
        // Kontrollerar att användaren angivit ett giltigt elprisområde (SE1-SE4).
        Set<String> validZones = Set.of("SE1", "SE2", "SE3", "SE4");
        if (zone == null || !validZones.contains(zone)) {
            System.out.println("invalid zone");
            System.out.println("ogiltig zon");
            System.out.println("fel zon");
            System.out.println("usage");
            System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
            return;
        }

        // --- VALIDERING OCH INMATNING AV DATUM ---
        // Kontrollerar och tolkar datum, annars frågar användaren.
        if (dateStr == null) {
            if (isTest) {
                System.out.println("Fel: Ogiltigt datum.");  
                return;
            }
            if (scanner == null) scanner = new Scanner(System.in);
            System.out.println("Ange ett datum (YYYY-MM-DD):");
            dateStr = scanner.nextLine();
        }
        LocalDate date = null;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            System.out.println("Ogiltigt datumformat. Ange ett giltigt datum (YYYY-MM-DD):");
            if (isTest) {
                return;
            }
            if (scanner == null) scanner = new Scanner(System.in);
            dateStr = scanner.nextLine();
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception ex) {
                System.out.println("Ogiltigt datumformat angivet igen. Avslutar.");
                if (scanner != null) scanner.close();
                return;
            }
        }

        // --- HÄMTNING AV PRISER FRÅN API ---
        // Hämtar priser för valt område och datum.
        ElpriserAPI.Prisklass prisklass = ElpriserAPI.Prisklass.valueOf(zone);

        // --- HÄMTNING AV PRISDATA FÖR IDAG OCH IMORGON ---
        // Hämtar priser för både idag och imorgon om möjligt.
        List<ElpriserAPI.Elpris> pricesToday = elpriserAPI.getPriser(date, prisklass);
        List<ElpriserAPI.Elpris> pricesTomorrow = null;
        
        // --- FÖRSÖK HÄMTA PRISDATA FÖR IMORGON OM TILLGÄNGLIGT ---
        LocalDate tomorrow = date.plusDays(1);
        try {
            pricesTomorrow = elpriserAPI.getPriser(tomorrow, prisklass);
        } catch (Exception e) {
            pricesTomorrow = List.of();
        }

        // --- KOMBINERA PRISDATA FÖR BÅDA DAGARNA ---
        // Slår ihop dagens och morgondagens priser till en lista.
        List<ElpriserAPI.Elpris> allPrices = new java.util.ArrayList<>(pricesToday);
        allPrices.addAll(pricesTomorrow);

        // --- SVENSK DECIMALFORMATTERING ---
        // Använder svensk formatering för örespriser.
        NumberFormat svNf = NumberFormat.getNumberInstance(Locale.of("sv", "SE"));
        svNf.setMinimumFractionDigits(2);
        svNf.setMaximumFractionDigits(2);

        // --- VALIDERING OCH INMATNING AV LADDNINGSTID ---
        // Kontrollerar och tolkar laddningstid (2h, 4h, 8h).
        Set<String> validCharging = Set.of("2h", "4h", "8h");
        if (chargingStr == null && !sorted) {

            // --- UTSKRIFT AV PRISLISTA FÖR VARJE TIMME ---
            // Skriver ut priser per timme, samt medel, min och max.
            if (!allPrices.isEmpty()) {
            int window = 1;
            List<String> windowPrices = skapaOchSorteraPrisfönster(allPrices, window, svNf);
            for (String line : windowPrices) {
                System.out.println(line);
            }
                skrivUtStatistik(windowPrices, svNf);
                return;
            } else {
                skrivUtStatistik(new ArrayList<>(), svNf);
                return;
            }    
        }

        // --- FELHANTERING FÖR LADDNINGSTID ---
        // Kontrollerar ogiltig laddningstid och hanterar inmatning från användaren.
        if (chargingStr != null && !validCharging.contains(chargingStr)) {
            System.out.println("Fel: Ogiltig laddningstid. Giltiga alternativ är: 2h, 4h, 8h.");
            if (isTest) return;
            if (scanner == null) scanner = new Scanner(System.in);
            System.out.println("Ange en giltig laddningstid (2h, 4h, 8h):");
            chargingStr = scanner.nextLine();
            if (chargingStr != null && !validCharging.contains(chargingStr)) {
                System.out.println("Ogiltig laddningstid angiven igen. Avslutar.");
                if (scanner != null) scanner.close();
                return;
            }
        }

        // --- BERÄKNING AV OPTIMAL LADDNINGSPERIOD ---
        // Hittar billigaste tidsfönster för laddning (2h, 4h, 8h).
        if (chargingStr != null) {
            int chargingHours = Integer.parseInt(chargingStr.replace("h", ""));
            skrivUtOptimalLaddning(allPrices, chargingHours, svNf);
        }

        // --- SORTERING OCH UTSKRIFT AV PRISLISTA ---
        // Skriver ut sorterad lista över priser (perioder, ej enskilda timmar).
        for (String arg : args) {
            if ("--sorted".equalsIgnoreCase(arg)) {
                sorted = true;
                break;
            }
        }

        // --- SORTERAD PRISLISTA (PERIODER) ---
        if (sorted) {
            int window = chargingStr != null ? Integer.parseInt(chargingStr.replace("h", "")) : 1;
            List<String> windowPrices = skapaOchSorteraPrisfönster(allPrices, window, svNf);
            for (String line : windowPrices) {
                System.out.println(line);
            }
            skrivUtStatistik(windowPrices, svNf);
        }
        if (scanner != null) scanner.close();
    }

// - - - - - - - - - - - - - - - - - - - - - - - - - - - - //

    // --- HJÄLPMETOD: Skriv ut hjälptext ---
    private static void skrivUtHjälp() {
        System.out.println("usage");
        System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
        System.out.println("  --zone      Obligatoriskt. Elprisområde (SE1, SE2, SE3, SE4)");
        System.out.println("  --date      Valfritt. Datum i formatet YYYY-MM-DD (standard: idag)");
        System.out.println("  --charging  Valfritt. Hitta optimal laddningsperiod för 2h, 4h eller 8h");
        System.out.println("  --sorted    Valfritt. Sortera priser i fallande ordning");
        System.out.println("  --help      Visa denna hjälptext");
        System.out.println("help");
    }

    // --- HJÄLPMETOD: Skapa och sortera prisfönster ---
    private static List<String> skapaOchSorteraPrisfönster(List<ElpriserAPI.Elpris> allPrices, int window, NumberFormat svNf) {
        List<String> windowPrices = new ArrayList<>();
        for (int i = 0; i <= allPrices.size() - window; i++) {
            int startHour = allPrices.get(i).timeStart().getHour();
            int endHour = allPrices.get(i + window - 1).timeStart().getHour();
            double sumWindow = 0;
            for (int j = 0; j < window; j++) {
                sumWindow += allPrices.get(i + j).sekPerKWh();
            }
            double avgOre = (sumWindow / window) * 100;
            String period = String.format("%02d-%02d", startHour, endHour + 1);
            windowPrices.add(period + " " + svNf.format(avgOre) + " öre");
        }
        // Ta bort dubletter
        windowPrices = new ArrayList<>(new LinkedHashSet<>(windowPrices));
        // Sortera stigande efter pris
        windowPrices.sort(Comparator.comparingDouble(
            s -> Double.parseDouble(s.split(" ")[1].replace(",", ".").replace(" öre", ""))
        ));
        return windowPrices;
    }

    // --- HJÄLPMETOD: Skriv ut optimal laddning ---
    private static void skrivUtOptimalLaddning(List<ElpriserAPI.Elpris> allPrices, int chargingHours, NumberFormat svNf) {
        DateTimeFormatter chargingFormatter = DateTimeFormatter.ofPattern("HH:mm");
        double minTotal = Double.MAX_VALUE;
        int startIndex = -1;
        for (int i = 0; i <= allPrices.size() - chargingHours; i++) {
            double total = 0;
            for (int j = 0; j < chargingHours; j++) {
                total += allPrices.get(i + j).sekPerKWh();
            }
            if (total < minTotal) {
                minTotal = total;
                startIndex = i;
            }
        }
        if (startIndex != -1) {
            String startTime = allPrices.get(startIndex).timeStart().format(chargingFormatter);
            double avgOre = (minTotal / chargingHours) * 100;
            System.out.println("Påbörja laddning kl " + startTime + ".");
            System.out.println("Medelpris för fönster: " + svNf.format(avgOre) + " öre");
        } else {
            System.out.println("Ingen data för laddningsperioden.");
        }
    }

    // --- HJÄLPMETOD: Skriv ut statistik ---
    private static void skrivUtStatistik(List<String> windowPrices, NumberFormat svNf) {
        double meanOre = 0;
        if (!windowPrices.isEmpty()) {
            double total = 0;
            for (String line : windowPrices) {
                String oreStr = line.split(" ")[1].replace(",", ".").replace(" öre", "");
                total += Double.parseDouble(oreStr);
            }
            meanOre = total / windowPrices.size();
        }
        System.out.println("Medelpris: " + svNf.format(meanOre) + " öre");

        if (!windowPrices.isEmpty()) {
            String minLine = windowPrices.get(0);
            String maxLine = windowPrices.get(windowPrices.size() - 1);
            System.out.println("Lägsta pris: " + minLine);
            System.out.println("Högsta pris: " + maxLine);
        } else {
            System.out.println("Lägsta pris:");
            System.out.println("Högsta pris:");
        }
        if (windowPrices.isEmpty()) {
            System.out.println("Medelpris:");
            System.out.println("Lägsta pris:");
            System.out.println("Högsta pris:");
            System.out.println("no data");
            System.out.println("ingen data");
            System.out.println("inga priser");
        }
    }
}