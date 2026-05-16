import React from 'react';
import Countdown from 'react-countdown';
import { Link } from 'react-router-dom';

interface RendererProps {
  hours: number;
  minutes: number;
  seconds: number;
  completed: boolean;
}
type RendererReturn = React.ReactNode;

interface CardDealsOfTheDayProps {
  title: string;
  to: string;
  /* endDate: Date; */
  endDate: number;
  children: React.ReactNode;
}
// Random component
const Completionist = () => <span>Deals End!</span>;

// Renderer callback with condition
const renderer = ({ hours, minutes, seconds, completed }: RendererProps): RendererReturn => {
  if (completed) {
    // Render a completed state
    return <Completionist />;
  } else {
    // Render a countdown
    return (
      <span className='text-muted small'>
        {hours}:{minutes}:{seconds} Left
      </span>
    );
  }
};

const CardDealsOfTheDay: React.FC<CardDealsOfTheDayProps> = props => {
  return (
    <div className='card'>
      <div className='card-body'>
        <h5 className='card-title pb-3 border-bottom'>
          {props.title} <i className='bi bi-stopwatch text-primary' /> <Countdown date={props.endDate} renderer={renderer} />
          <span className='float-end'>
            <Link to={props.to} className='btn btn-sm btn-outline-primary'>
              View All
            </Link>
          </span>
        </h5>
        {props.children}
      </div>
    </div>
  );
};

export default CardDealsOfTheDay;
