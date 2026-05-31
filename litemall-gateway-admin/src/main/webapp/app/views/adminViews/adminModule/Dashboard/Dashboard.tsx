import { CategoryScale, Chart as ChartJS, ChartData, ChartOptions, Legend, LinearScale, LineElement, PointElement, Title, Tooltip } from 'chart.js';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { fetchOrderStats } from 'app/shared/reducers/private/catalogMgn/adminStateSlice';
import * as React from 'react';
import { Card, Col, Row, Spinner, Table } from 'react-bootstrap';
import { Line } from 'react-chartjs-2';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

const brandInfo = '#63c2de';
const brandSuccess = '#4dbd74';

const chartOpts: ChartOptions<'line'> = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: true } },
};

const Dashboard: React.FC = () => {
  const dispatch = useAppDispatch();
  const { rows, totals, loading, unavailable, errorMessage } = useAppSelector(state => state.adminState);

  React.useEffect(() => {
    dispatch(fetchOrderStats());
  }, [dispatch]);

  const labels = rows.map(r => r.day);

  const ordersChart: ChartData<'line'> = {
    labels,
    datasets: [
      {
        label: 'Orders',
        backgroundColor: 'transparent',
        borderColor: brandInfo,
        pointHoverBackgroundColor: '#fff',
        borderWidth: 2,
        data: rows.map(r => r.orders),
      },
      {
        label: 'Customers',
        backgroundColor: 'transparent',
        borderColor: brandSuccess,
        pointHoverBackgroundColor: '#fff',
        borderWidth: 2,
        data: rows.map(r => r.customers),
      },
    ],
  };

  const amountChart: ChartData<'line'> = {
    labels,
    datasets: [
      {
        label: 'Revenue',
        backgroundColor: 'transparent',
        borderColor: brandSuccess,
        pointHoverBackgroundColor: '#fff',
        borderWidth: 2,
        data: rows.map(r => r.amount),
      },
    ],
  };

  return (
    <div className='animated fadeIn'>
      <h4 className='my-3'>Order statistics {loading && <Spinner animation='border' size='sm' className='ms-2' />}</h4>

      {unavailable && (
        <div className='alert alert-warning'>
          {errorMessage || 'Order statistics are not available yet.'}{' '}
          <span className='text-muted small'>(Pending the order service&apos;s /srv/order/admin/stat endpoint.)</span>
        </div>
      )}
      {!unavailable && errorMessage && <div className='alert alert-danger'>{errorMessage}</div>}

      <Row className='mb-3'>
        <Col sm='4'>
          <Card className='text-white bg-primary'>
            <Card.Body>
              <div className='h4 mb-0'>{totals.orders}</div>
              <small>Total orders</small>
            </Card.Body>
          </Card>
        </Col>
        <Col sm='4'>
          <Card className='text-white bg-info'>
            <Card.Body>
              <div className='h4 mb-0'>{totals.customers}</div>
              <small>Customers</small>
            </Card.Body>
          </Card>
        </Col>
        <Col sm='4'>
          <Card className='text-white bg-success'>
            <Card.Body>
              <div className='h4 mb-0'>¥{totals.amount.toFixed(2)}</div>
              <small>Revenue</small>
            </Card.Body>
          </Card>
        </Col>
      </Row>

      {rows.length === 0 ? (
        !loading && !unavailable && <div className='text-muted text-center py-5'>No order statistics for this period.</div>
      ) : (
        <>
          <Row>
            <Col lg={6}>
              <Card className='mb-3'>
                <Card.Header>Orders &amp; customers over time</Card.Header>
                <Card.Body>
                  <div style={{ height: 300 }}>
                    <Line data={ordersChart} options={chartOpts} />
                  </div>
                </Card.Body>
              </Card>
            </Col>
            <Col lg={6}>
              <Card className='mb-3'>
                <Card.Header>Revenue over time</Card.Header>
                <Card.Body>
                  <div style={{ height: 300 }}>
                    <Line data={amountChart} options={chartOpts} />
                  </div>
                </Card.Body>
              </Card>
            </Col>
          </Row>

          <Card>
            <Card.Header>Daily breakdown</Card.Header>
            <Card.Body>
              <Table responsive hover size='sm'>
                <thead className='table-light'>
                  <tr>
                    <th>Day</th>
                    <th className='text-end'>Orders</th>
                    <th className='text-end'>Customers</th>
                    <th className='text-end'>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.day}>
                      <td>{r.day}</td>
                      <td className='text-end'>{r.orders}</td>
                      <td className='text-end'>{r.customers}</td>
                      <td className='text-end'>¥{r.amount.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </Card.Body>
          </Card>
        </>
      )}
    </div>
  );
};

export default Dashboard;
