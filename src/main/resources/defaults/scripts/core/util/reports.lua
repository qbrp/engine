---@diagnostic disable: inject-field, param-type-mismatch
---@class (partial) ReportCollector
local reports_collector = engine.reports_collector

---@class ReportDraft
---@field message string
---@field phase CompilationPhase
---@field namespace string?
---@field location ReportLocation?
---@field target ReportTarget?

---@param report ReportDraft
function reports_collector:report_error(report)
    report.severity = "error"
    self:report(report)
end

---@param report ReportDraft
function reports_collector:report_warn(report)
    report.severity = "warn"
    self:report(report)
end