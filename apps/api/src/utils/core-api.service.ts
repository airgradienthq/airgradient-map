import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import { Request } from 'express';

/**
 * Service for forwarding authenticated requests to AirGradient's Core API.
 * Automatically extracts cookies from incoming requests and forwards them.
 */
@Injectable()
export class CoreApiService {
  private readonly logger = new Logger(CoreApiService.name);
  private readonly coreApiUrl: string;

  constructor(private readonly httpService: HttpService) {
    this.coreApiUrl = process.env.CORE_API_URL;
    this.logger.log(`Core API URL: ${this.coreApiUrl}`);
  }

  /**
   * Extract cookies from Express request
   * @param req Express request object
   * @returns Cookie header string or undefined
   */
  extractCookies(req: Request): string | undefined {
    const cookies = req.headers.cookie;

    if (cookies) {
      this.logger.debug(`Extracted cookies from request: ${cookies.substring(0, 50)}...`);
    } else {
      this.logger.debug('No cookies found in request');
    }

    return cookies;
  }

  /**
   * Forward a GET request to Core API with cookies
   * @param req Express request (to extract cookies from)
   * @param path Core API path (e.g., '/auth/user')
   * @returns Response from Core API
   */
  async get<T>(req: Request, path: string): Promise<T> {
    const url = `${this.coreApiUrl}${path}`;
    const cookies = this.extractCookies(req);
    this.logger.debug(cookies);

    this.logger.log(`Forwarding GET ${path} to Core API`);

    try {
      const { data } = await firstValueFrom(
        this.httpService.get<T>(url, {
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...(cookies && { Cookie: cookies }),
          },
        }),
      );

      return data;
    } catch (error) {
      this.logger.error(`Core API GET ${path} failed: ${error.message}`);
      throw new Error(`Core API error: ${error.response?.status || 500}`);
    }
  }

  /**
   * Forward a POST request to Core API with cookies
   * @param req Express request (to extract cookies from)
   * @param path Core API path
   * @param body Request body
   * @returns Response from Core API
   */
  async post<T>(req: Request, path: string, body: unknown): Promise<T> {
    const url = `${this.coreApiUrl}${path}`;
    const cookies = this.extractCookies(req);

    this.logger.log(`Forwarding POST ${path} to Core API`);

    try {
      const { data } = await firstValueFrom(
        this.httpService.post<T>(url, body, {
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...(cookies && { Cookie: cookies }),
          },
        }),
      );

      return data;
    } catch (error) {
      this.logger.error(`Core API POST ${path} failed: ${error.message}`);
      throw new Error(`Core API error: ${error.response?.status || 500}`);
    }
  }

  /**
   * Forward a PATCH request to Core API with cookies
   * @param req Express request (to extract cookies from)
   * @param path Core API path
   * @param body Request body
   * @returns Response from Core API
   */
  async patch<T>(req: Request, path: string, body: unknown): Promise<T> {
    const url = `${this.coreApiUrl}${path}`;
    const cookies = this.extractCookies(req);

    this.logger.log(`Forwarding PATCH ${path} to Core API`);

    try {
      const { data } = await firstValueFrom(
        this.httpService.patch<T>(url, body, {
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...(cookies && { Cookie: cookies }),
          },
        }),
      );

      return data;
    } catch (error) {
      this.logger.error(`Core API PATCH ${path} failed: ${error.message}`);
      throw new Error(`Core API error: ${error.response?.status || 500}`);
    }
  }

  /**
   * Forward a DELETE request to Core API with cookies
   * @param req Express request (to extract cookies from)
   * @param path Core API path
   * @returns void
   */
  async delete(req: Request, path: string): Promise<void> {
    const url = `${this.coreApiUrl}${path}`;
    const cookies = this.extractCookies(req);

    this.logger.log(`Forwarding DELETE ${path} to Core API`);

    try {
      await firstValueFrom(
        this.httpService.delete(url, {
          headers: {
            Accept: 'application/json',
            ...(cookies && { Cookie: cookies }),
          },
        }),
      );
    } catch (error) {
      this.logger.error(`Core API DELETE ${path} failed: ${error.message}`);
      throw new Error(`Core API error: ${error.response?.status || 500}`);
    }
  }

  /**
   * Check if cookies exist in request
   * Useful for determining if request is authenticated
   * @param req Express request
   * @returns true if cookies exist
   */
  hasCookies(req: Request): boolean {
    return !!req.headers.cookie;
  }
}
