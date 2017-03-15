# fit-to-edn

Parsing .fit binary files (leveraging Garmin SDK through Clojure Java interop), 
retaining the original streaming capabilities of the Java API, exposing them
through `core.async` channels.

All processing is defined as transducer application over channels

## Usage

Finding maximal 1min power ever achieved in the directory containing .fit records

```clj
fit-to-edn.core> (max-power-interval
                  (list-fit-files "/Users/janherich/Documents/Training-data")
                  60)
566.56665
```

## License

Copyright © 2017 Jan Herich

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
