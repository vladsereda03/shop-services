{{/*
Common metadata labels for a service. Call with a dict carrying the service
name and the root context:
  {{- include "shop.labels" (dict "name" $name "root" $) | nindent 4 }}
*/}}
{{- define "shop.labels" -}}
{{ include "shop.selectorLabels" (dict "name" .name) }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
helm.sh/chart: {{ .root.Chart.Name }}-{{ .root.Chart.Version }}
{{- end -}}

{{/*
Selector labels — the stable subset used in spec.selector and pod labels; must
not change across upgrades. Call with a dict carrying the service name:
  {{- include "shop.selectorLabels" (dict "name" $name) | nindent 6 }}
*/}}
{{- define "shop.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: shop
{{- end -}}
