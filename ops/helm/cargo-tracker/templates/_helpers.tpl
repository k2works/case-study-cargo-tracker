{{/* 共通ラベル */}}
{{- define "cargo-tracker.labels" -}}
app.kubernetes.io/part-of: cargo-tracker
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/* イメージ参照 cargo-tracker/<ms>:<tag> */}}
{{- define "cargo-tracker.image" -}}
{{- printf "%s/%s:%s" .registry .name .tag -}}
{{- end -}}
