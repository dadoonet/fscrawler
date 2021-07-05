curl -XPOST \
--url 'http://localhost:3002/api/ws/v1/sources/' \
--header 'Content-Type: application/json' \
-u "elastic:changeme" \
--data '{
  "name": "Test",
  "schema": {
    "title": "text",
    "url": "text",
    "my_field": "text"
  },
  "display": {
    "title_field": "title",
    "url_field": "url",
    "detail_fields": [
      {
        "field_name": "my_field",
        "label": "My Field"
      }
    ],
    "color": "#111111"
  },
  "is_searchable": true
}'
