(ns fit-to-edn.record
  (:import [com.garmin.fit RecordMesg Field FieldBase Factory]))

(set! *warn-on-reflection* true)

(def ^:private fields
  #{RecordMesg/TimestampFieldNum
    RecordMesg/AltitudeFieldNum
    RecordMesg/GradeFieldNum
    RecordMesg/SpeedFieldNum
    RecordMesg/CadenceFieldNum
    RecordMesg/PowerFieldNum
    RecordMesg/HeartRateFieldNum
    RecordMesg/TemperatureFieldNum
    RecordMesg/PositionLatFieldNum
    RecordMesg/PositionLongFieldNum})

(defn parse-record-mesg
  [^RecordMesg mesg]
  (reduce (fn [acc ^long field-num]
            (let [^FieldBase field (first (.getOverrideField mesg field-num))
                  ^Field profileField (Factory/createField (.getNum mesg) field-num)]
              (if (and field profileField)
                (assoc acc (keyword (.getName profileField)) (.getValue field))
                acc)))
          {}
          fields))
