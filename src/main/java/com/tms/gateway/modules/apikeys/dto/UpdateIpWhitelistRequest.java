package com.tms.gateway.modules.apikeys.dto;

/**
 * Request to update IP whitelist for an API key.
 *
 * @param ipWhitelist
 *            Comma-separated list of IP addresses or CIDR blocks. Empty or null
 *            means no restriction (allow all IPs).
 */
public record UpdateIpWhitelistRequest(String ipWhitelist) {
}
