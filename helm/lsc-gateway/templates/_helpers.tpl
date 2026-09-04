{{- define "lsc-gateway.serviceAccountName" -}}
{{- if .Values.serviceAccount.name -}}
{{- .Values.serviceAccount.name -}}
{{- else if .Values.serviceAccount.create -}}
lsc-gateway
{{- else -}}
default
{{- end -}}
{{- end -}}
