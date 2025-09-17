/*
 * Name: Amsu Warner
 * Date: 2025-09-16
 * Purpose: Gale–Shapley stable matching (man-optimal) using only basic data structures implemented here.
 *
 * Compile (on csstu / any Java 17+):
 *   javac GSMatching.java
 *
 * Run:
 *   java GSMatching <menPrefsFile> <womenPrefsFile> <outputFile>
 *
 * Example:
 *   java GSMatching boys girls couples
 *
 * Notes:
 * - Runs in O(n^2).
 * - Men are 1..n, women are 1..n.
 * - Men’s preferences stored as custom singly linked lists (each proposal pops the head).
 * - Women’s preferences stored as rank arrays: rank[w][m] = preference order (smaller = preferred).
 * - Unmatched men managed with a custom circular array queue.
 * - Basic error checks for files/args; assumes well-formed preference data as specified.
 */

import java.io.*;

public class GSMatching {

    /* ------------ Simple singly linked list for a man's preferences ------------ */
    private static final class Node {
        int val; Node next;
        Node(int v) { val = v; }
    }

    private static final class PrefList {
        private Node head, tail;
        PrefList() {}
        void addLast(int w) {
            Node n = new Node(w);
            if (head == null) { head = tail = n; }
            else { tail.next = n; tail = n; }
        }
        boolean isEmpty() { return head == null; }
        // Pop the first (next preferred) woman; returns -1 if empty
        int popFirst() {
            if (head == null) return -1;
            int v = head.val;
            head = head.next;
            if (head == null) tail = null;
            return v;
        }
    }

    /* ------------ Simple circular queue for unmatched men ------------ */
    private static final class IntQueue {
        private final int[] a;
        private int head = 0, tail = 0, size = 0;
        IntQueue(int capacity) { a = new int[capacity]; }
        boolean isEmpty() { return size == 0; }
        void enqueue(int x) {
            if (size == a.length) throw new IllegalStateException("Queue overflow");
            a[tail] = x;
            tail = (tail + 1) % a.length;
            size++;
        }
        int dequeue() {
            if (size == 0) throw new IllegalStateException("Queue underflow");
            int x = a[head];
            head = (head + 1) % a.length;
            size--;
            return x;
        }
    }

    /* ------------ File parsing helpers ------------ */
    private static BufferedReader openReader(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) throw new FileNotFoundException("File not found: " + path);
        return new BufferedReader(new FileReader(f));
    }

    private static String readNonEmptyLine(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) return line;
        }
        return null;
    }

    private static int parseN(BufferedReader br) throws IOException {
        String first = readNonEmptyLine(br);
        if (first == null) throw new IOException("Missing 'n' (first line).");
        return Integer.parseInt(first.trim());
    }

    /* ------------ Main algorithm ------------ */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java GSMatching <menPrefsFile> <womenPrefsFile> <outputFile>");
            System.exit(1);
        }

        String menFile = args[0];
        String womenFile = args[1];
        String outFile = args[2];

        try (BufferedReader menBR = openReader(menFile);
             BufferedReader womenBR = openReader(womenFile)) {

            // Read n and men's preference lists (as singly linked lists)
            int nMen = parseN(menBR);
            PrefList[] menPrefs = new PrefList[nMen + 1]; // 1..n
            for (int m = 1; m <= nMen; m++) {
                String line = readNonEmptyLine(menBR);
                if (line == null) throw new IOException("Missing preference line for man " + m);
                String[] parts = line.trim().split("\\s+");
                if (parts.length != nMen) {
                    throw new IOException("Expected " + nMen + " preferences for man " + m + ", got " + parts.length);
                }
                PrefList list = new PrefList();
                for (String p : parts) list.addLast(Integer.parseInt(p));
                menPrefs[m] = list;
            }

            // Read n and women preferences, convert to rank arrays
            int nWomen = parseN(womenBR);
            if (nWomen != nMen) {
                throw new IOException("Number of men (" + nMen + ") != number of women (" + nWomen + ")");
            }
            int[][] rank = new int[nMen + 1][nMen + 1]; // rank[w][m]
            for (int w = 1; w <= nWomen; w++) {
                String line = readNonEmptyLine(womenBR);
                if (line == null) throw new IOException("Missing preference line for woman " + w);
                String[] parts = line.trim().split("\\s+");
                if (parts.length != nMen) {
                    throw new IOException("Expected " + nMen + " preferences for woman " + w + ", got " + parts.length);
                }
                for (int i = 0; i < parts.length; i++) {
                    int man = Integer.parseInt(parts[i]);
                    // lower rank value = more preferred (1 is best)
                    rank[w][man] = i + 1;
                }
            }

            // Matches: 0 = unmatched
            int[] manToWoman = new int[nMen + 1];
            int[] womanToMan = new int[nMen + 1];

            // Initialize queue with all men
            IntQueue freeMen = new IntQueue(nMen * nMen + 5); // generous capacity
            for (int m = 1; m <= nMen; m++) freeMen.enqueue(m);

            // Gale–Shapley: each man proposes to next woman on his list until matched
            while (!freeMen.isEmpty()) {
                int m = freeMen.dequeue();

                // If this man has no one left to propose to, skip (shouldn't happen with valid inputs)
                if (menPrefs[m].isEmpty()) continue;

                int w = menPrefs[m].popFirst();

                if (womanToMan[w] == 0) {
                    // Woman w is free -> engage m and w
                    womanToMan[w] = m;
                    manToWoman[m] = w;
                } else {
                    int m2 = womanToMan[w]; // current fiancé
                    // If woman w prefers m over m2, she dumps m2
                    if (rank[w][m] < rank[w][m2]) {
                        womanToMan[w] = m;
                        manToWoman[m] = w;
                        manToWoman[m2] = 0;     // m2 becomes free
                        // if m2 still has options, enqueue him back
                        if (!menPrefs[m2].isEmpty()) freeMen.enqueue(m2);
                    } else {
                        // w rejects m; if m has more options, he re-enters the queue
                        if (!menPrefs[m].isEmpty()) freeMen.enqueue(m);
                    }
                }
            }

            // Write output: man woman per line, for man = 1..n
            try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
                for (int m = 1; m <= nMen; m++) {
                    pw.println(m + " " + manToWoman[m]);
                }
            }

        } catch (FileNotFoundException fnf) {
            System.err.println("Error: " + fnf.getMessage());
            System.exit(2);
        } catch (IOException ioe) {
            System.err.println("I/O Error: " + ioe.getMessage());
            System.exit(3);
        } catch (NumberFormatException nfe) {
            System.err.println("Parse Error: Non-integer where integer expected. " + nfe.getMessage());
            System.exit(4);
        }
    }
}