import { Test, TestingModule } from '@nestjs/testing';
import { AqiStandardsController } from './aqi-standards.controller';
import { AqiStandardsModule } from './aqi-standards.module';

type ResponseMock = {
  headers: Record<string, string>;
  statusCode?: number;
  setHeader: jest.Mock;
  status: jest.Mock;
};

const createResponseMock = (): ResponseMock => {
  const headers: Record<string, string> = {};
  const res: ResponseMock = {
    headers,
    statusCode: 200,
    setHeader: jest.fn((key: string, value: string) => {
      headers[key] = value;
    }),
    status: jest.fn((code: number) => {
      res.statusCode = code;
      return res;
    }),
  };
  return res;
};

describe('AqiStandardsController', () => {
  let controller: AqiStandardsController;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AqiStandardsModule],
    }).compile();

    controller = moduleFixture.get<AqiStandardsController>(AqiStandardsController);
  });

  it('returns the AQI standards payload with cache headers', async () => {
    const res = createResponseMock();
    const payload = await controller.getAqiStandards(res as any, undefined);

    expect(payload).toBeDefined();
    expect(payload).toHaveProperty('visibility');
    expect(payload).toHaveProperty('standards');
    expect(Array.isArray((payload as any).standards)).toBe(true);

    expect(res.setHeader).toHaveBeenCalledWith('Cache-Control', expect.any(String));
    expect(res.setHeader).toHaveBeenCalledWith('ETag', expect.any(String));
  });

  it('returns 304 when ETag matches If-None-Match', async () => {
    const firstRes = createResponseMock();
    await controller.getAqiStandards(firstRes as any, undefined);
    const etag = firstRes.setHeader.mock.calls.find(([header]) => header === 'ETag')?.[1];

    const secondRes = createResponseMock();
    const result = await controller.getAqiStandards(secondRes as any, etag);

    expect(secondRes.status).toHaveBeenCalledWith(304);
    expect(result).toBeUndefined();
  });
});
