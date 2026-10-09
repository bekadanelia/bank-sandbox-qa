import { Router } from 'express';
import { pool } from '../db.js';

export const fxRatesRouter = Router();

fxRatesRouter.get('/fx-rates', async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM fx_rates ORDER BY base_currency, quote_currency');
  res.json({
    items: rows.map((row) => ({
      baseCurrency: row.base_currency,
      quoteCurrency: row.quote_currency,
      rate: row.rate,
      updatedAt: row.updated_at.toISOString(),
    })),
  });
});
