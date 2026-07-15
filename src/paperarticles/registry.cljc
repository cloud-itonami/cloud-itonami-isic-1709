(ns paperarticles.registry
  "Pure-function domain logic for the ISIC 1709 (other paper and
  paperboard articles) plant-operations coordination actor -- product
  batch/equipment verification, shipment-quantity recompute,
  product-grade validation, basis-weight (gsm) and moisture-content
  (%) plausibility validation, and draft maintenance-schedule/
  shipment-coordination record construction.

  Chosen concrete illustration of ISIC 1709's residual 'other paper
  and paperboard articles' category: molded-pulp/paperboard paper
  tableware manufacturing (paper plates/bowls/trays, coated and
  uncoated paper cups) -- see README for why this illustration was
  picked and how it stands in for this category's other n.e.c. product
  lines (filter paper, wallpaper base stock).

  This vertical has NO pre-existing `kotoba-lang/paperarticles`-style
  capability library to wrap (verified: no such repo exists, mirroring
  `cloud-itonami-isic-1701`/`cloud-itonami-isic-1702`'s own paper
  verticals). The domain logic therefore lives here as pure functions,
  re-verified INDEPENDENTLY by `paperarticles.governor` -- the same
  'ground truth, not self-report' discipline every sibling actor's own
  registry establishes: never trust a proposal's own self-reported
  quantity/status when the inputs needed to recompute it independently
  are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real mill-operations system. It builds the DRAFT record
  a plant coordinator would keep (a scheduled maintenance window, a
  coordinated shipment), not the act of actuating pulp-molding-press/
  die-cutter/converting-line equipment or dispatching a real freight
  carrier (this actor NEVER does either -- see README `What this actor
  does NOT do`).")

;; ----------------------------- constants -----------------------------

(def valid-product-grades
  "The closed set of paper/paperboard-article product-grade values a
  production-batch record may declare, spanning molded-pulp tableware,
  coated/uncoated paperboard tableware, and this category's other
  n.e.c. product lines (filter paper, wallpaper base stock). Anything
  else is a fabricated/unrecognized grade -- the governor HARD-holds
  rather than let an invented grade pass through."
  #{:molded-pulp-plate :molded-pulp-bowl :molded-pulp-tray
    :coated-paperboard-cup :uncoated-paperboard-cup
    :paper-plate-standard :paper-plate-premium
    :filter-paper-grade-1 :filter-paper-grade-2
    :wallpaper-base-stock})

(def basis-weight-min-gsm
  "Physical floor for a basis-weight (grammage) reading -- a paper/
  paperboard sample cannot have negative mass per unit area."
  0.0)

(def basis-weight-max-gsm
  "Physical ceiling for a basis-weight (gsm) reading. Even the
  heaviest molded-pulp tableware or multi-ply paperboard tableware
  stock does not plausibly exceed this -- a reading above it is
  implausible sensor/instrument data, not a real batch."
  500.0)

(def moisture-content-min-pct
  "Physical floor for a moisture-content reading (a paper/paperboard
  sample cannot have negative moisture content)."
  0.0)

(def moisture-content-max-pct
  "Physical ceiling for a moisture-content (%) reading. Finished
  paper/paperboard articles run a few percent moisture content in
  normal production; a reading above this ceiling is implausible
  sensor/instrument data (or a soaked/ruined batch), not a real
  in-spec batch."
  15.0)

;; ----------------------------- equipment checks -----------------------------

(defn equipment-verified?
  "Ground-truth check: has `equipment`'s own record been marked
  verified (i.e. it has actually been inspected/commissioned and
  registered in the SSoT, not merely referenced from an unverified
  maintenance request)? A pure predicate over the equipment's own
  permanent field -- no proposal inspection needed."
  [equipment]
  (true? (:verified? equipment)))

(defn equipment-registered?
  "Ground-truth check: does `equipment`'s own record carry a
  `:registered?` true flag (i.e. it is on file in the plant's
  equipment registry)? Scheduling maintenance against equipment that
  is not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [equipment]
  (true? (:registered? equipment)))

(defn equipment-ready?
  "Combined ground-truth gate: the equipment must be both `verified?`
  AND `registered?` before ANY maintenance may be scheduled against
  it. Two independent facts on the equipment's own permanent record,
  neither inferred from the advisor's own rationale."
  [equipment]
  (and (equipment-verified? equipment) (equipment-registered? equipment)))

;; ----------------------------- batch checks -----------------------------

(defn batch-verified?
  "Ground-truth check: has `batch`'s own record been marked verified
  (i.e. its grade/quantity/quality claims have actually been
  QC-inspected, not merely logged from an unverified intake patch)?"
  [batch]
  (true? (:verified? batch)))

(defn batch-registered?
  "Ground-truth check: is `batch`'s own record on file in the plant's
  production ledger? Coordinating a shipment against a batch that is
  not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [batch]
  (true? (:registered? batch)))

(defn batch-ready?
  "Combined ground-truth gate: the batch must be both `verified?` AND
  `registered?` before ANY shipment may be coordinated against it."
  [batch]
  (and (batch-verified? batch) (batch-registered? batch)))

(defn shipment-quantity-exceeded?
  "Ground-truth check for a `:coordinate-shipment` proposal: would
  `shipped-to-date-units` + `new-quantity-units` exceed `batch`'s own
  recorded `:quantity-units` (the batch's own logged production
  quantity, e.g. cases of tableware)? Needs no proposal inspection or
  stored-verdict lookup -- its inputs are permanent fields already on
  the batch's own record, the same shape every sibling actor's own
  cost/total-matching check uses."
  [batch new-quantity-units]
  (let [capacity (:quantity-units batch)
        so-far (:shipped-quantity-units batch 0.0)]
    (and (number? capacity)
         (number? new-quantity-units)
         (> (+ (double so-far) (double new-quantity-units)) (double capacity)))))

(defn grade-valid?
  "Is `grade` one of the closed, known paper/paperboard-article
  product-grade values? nil/blank is treated as invalid (a
  production-batch patch must declare a real grade, not omit it
  silently)."
  [grade]
  (contains? valid-product-grades grade))

(defn basis-weight-valid?
  "Is `gsm` a physically plausible basis-weight (grammage) reading?
  Rejects nil, non-numbers, negative values, and values beyond
  `basis-weight-max-gsm` -- a fabricated or sensor-error reading,
  never let through as a real batch fact."
  [gsm]
  (and (number? gsm)
       (>= (double gsm) basis-weight-min-gsm)
       (<= (double gsm) basis-weight-max-gsm)))

(defn moisture-content-valid?
  "Is `pct` a physically plausible moisture-content (%) reading?
  Rejects nil, non-numbers, negative values, and values beyond
  `moisture-content-max-pct` -- a fabricated or sensor-error reading,
  never let through as a real batch fact."
  [pct]
  (and (number? pct)
       (>= (double pct) moisture-content-min-pct)
       (<= (double pct) moisture-content-max-pct)))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human plant supervisor's/shipping approver's act, not this
  actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-maintenance
  "Validate + construct the MAINTENANCE-SCHEDULE DRAFT -- a proposed
  pulp-molding-press/die-cutter/converting-line maintenance window
  against a verified, registered piece of equipment. Pure function --
  does not actuate converting-line equipment or execute any
  maintenance; it builds the RECORD a plant coordinator would keep.
  `paperarticles.governor` independently re-verifies the equipment's
  own verified/registered ground truth before this is ever allowed to
  commit."
  [maintenance-id equipment-id sequence]
  (when-not (and maintenance-id (not= maintenance-id ""))
    (throw (ex-info "maintenance: maintenance_id required" {})))
  (when-not (and equipment-id (not= equipment-id ""))
    (throw (ex-info "maintenance: equipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "maintenance: sequence must be >= 0" {})))
  (let [maintenance-number (str "MNT-" (zero-pad sequence 6))
        record {"record_id" maintenance-number
                "kind" "maintenance-schedule-draft"
                "maintenance_id" maintenance-id
                "equipment_id" equipment-id
                "immutable" true}]
    {"record" record "maintenance_number" maintenance-number
     "certificate" (unsigned-certificate "MaintenanceSchedule" maintenance-number maintenance-number)}))

(defn register-shipment
  "Validate + construct the SHIPMENT-COORDINATION DRAFT -- a proposed
  outbound paper-article shipment against a verified, registered
  production batch. Pure function -- does not dispatch any real
  freight carrier; it builds the RECORD a plant coordinator would
  keep. `paperarticles.governor` independently re-verifies the
  shipment's own claimed quantity against `shipment-quantity-
  exceeded?`, before this is ever allowed to commit."
  [shipment-id sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment: shipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str "SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-coordination-draft"
                "shipment_id" shipment-id
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentCoordination" shipment-number shipment-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
