# fit-to-edn

Parsing .fit binary files (leveraging Garmin SDK through Clojure Java interop), 
retaining the original streaming capabilities of the Java API, exposing them
through `core.async` channels.

All processing is defined as transducer application over channels

## Usage

Clone the project, and run `lein repl` in the project root, the repl will start
in the `fit-to-edn.core` namespace by default and you can start experimenting.

1. Finding maximal 1min power ever achieved in the directory containing .fit records:

```clj
fit-to-edn.core> (query-files
                  (comp q/power (q/max-average-interval 60))
                  q/max-aggregate
                  (f/format-query-result f/power)
                  (list-fit-files "/Users/janherich/Documents/Training-data"))
566.567 watt
```

2. The same for speed, but now we also want to know the activity where it happened:

```clj
fit-to-edn.core> (query-files
                  (comp q/speed (q/max-average-interval 60))
                  q/max-aggregate
                  (f/format-query-result f/speed-kmh #(.getName %))
                  (list-fit-files "/Users/janherich/Documents/Training-data"))
{:query-result "60.806 km/h" :activity "160726090112.fit"}
```

3. Finding maximal normalized-power ever achieved, we want to know the activity as well:

```clj
fit-to-edn.core> (query-files
                  (comp q/power q/normalized-average)
                  q/max-aggregate
                  (f/format-query-result f/power #(.getName %))
                  (list-fit-files "/Users/janherich/Documents/Training-data"))
{:query-result "304.144 watt", :activity "160820193938.fit"}
```

4. Overall best average moving speed ever achieved

```clj
fit-to-edn.core> (query-files
                  (comp q/moving-speed q/average)
                  q/max-aggregate
                  (f/format-query-result f/speed-kmh)
                  (list-fit-files "/Users/janherich/Documents/Training-data"))
39.077 km/h
```

## License

Copyright © 2017 Jan Herich

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
