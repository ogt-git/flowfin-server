# FlowFin Nginx Proxy Deployment

Production Docker exposes the Spring Boot app only on `127.0.0.1:8080`.
Nginx must listen on port `80` on the EC2 host and proxy `/api/` requests to
that local backend.

## Apply

Run these commands on the EC2 host from `~/flowfin-server`.

```bash
docker compose -f docker-compose.prod.yml up -d --build
curl -i http://127.0.0.1:8080/api/community

sudo apt-get update
sudo apt-get install -y nginx
sudo cp deploy/nginx/flowfin-server.conf /etc/nginx/sites-available/flowfin-server
sudo ln -sf /etc/nginx/sites-available/flowfin-server /etc/nginx/sites-enabled/flowfin-server
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx

curl -i http://127.0.0.1/api/community
```

## Expected Checks

Backend direct check:

```bash
curl -i http://127.0.0.1:8080/api/community
```

Nginx proxy check:

```bash
curl -i http://127.0.0.1/api/community
```

If the backend direct check works but the Nginx proxy check fails with
`Could not connect to server`, Nginx is not listening on port `80`.

If the proxy check returns `502 Bad Gateway`, Nginx is running but cannot reach
the Spring Boot app on `127.0.0.1:8080`.

Useful diagnostics:

```bash
sudo systemctl status nginx --no-pager
sudo ss -lntp | grep -E ':80|:8080'
docker ps
docker logs --tail=100 flowfin-app
sudo tail -n 100 /var/log/nginx/flowfin-error.log
```
